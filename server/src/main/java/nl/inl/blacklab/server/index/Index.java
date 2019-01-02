package nl.inl.blacklab.server.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexTooOld;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.search.BlsCache;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * A wrapper of sorts around {@link BlackLabIndexImpl}, which is the main blacklab-core
 * interface to an index.
 *
 * This is the main class used to interface with a corpus/index in
 * Blacklab-Server. Note the difference between an Index, which is a searchable
 * collection of documents, and the _act_ of Indexing, adding new data to an
 * Index. Blacklab-Server manages indices centrally using the
 * {@link IndexManager}. These handles are managed through this Index class.
 *
 * References to an Index should not be held for extended amounts of time
 * (minutes) (other than by the IndexManager that is), as an Index might
 * suddenly be closed, or begin indexing new data, or even be deleted.
 */
public class Index {

    //private static final Logger logger = LogManager.getLogger(Index.class);

    private static final String SHARE_WITH_USERS_FILENAME = ".shareWithUsers";

    public enum IndexStatus {
        EMPTY, // index has just been created. can be added to but not searched.
        AVAILABLE, // index is available for searching and adding to
        INDEXING; // index is busy, files are being added to it

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * Sort all public indices first, then sort alphabetically within all public and
     * private indices.
     */
    public static final Comparator<Index> COMPARATOR = new Comparator<Index>() {
        @Override
        public int compare(Index o1, Index o2) {
            // Sort public before private
            boolean o1priv = o1.isUserIndex();
            boolean o2priv = o2.isUserIndex();
            if (o1priv != o2priv)
                return o1priv ? 1 : -1;

            // Sort rest case-insensitively
            return o1.getId().toLowerCase().compareTo(o2.getId().toLowerCase());
        }
    };

    private final String id;

    private final File dir;

    private SearchManager searchMan;

    /**
     * Only one of these can be set at a time. The index is closed and cleared
     * when an indexer is requested. Running searches are cancelled when this
     * happens. The Indexer is cleared the first time a search is started after it
     * the Indexer has finished indexing (meaning close() has been called on it). In
     * addition, while an index is still running, no new Indexers can be created.
     */
    private BlackLabIndex index;
    private Indexer indexer;

    /** List of users who may access this index (read-only). */
    private List<String> shareWithUsers = new ArrayList<>();

    /** File where the list of users to share with is stored */
    private File shareWithUsersFile;

    /**
     * NOTE: Index does not support creating a new index from scratch for now,
     * instead use {@link IndexManager#createIndex(String, String, String)}
     *
     * @param indexId name of this index, including any username if this is a user
     *            index
     * @param dir directory of this index
     * @param searchMan search manager
     * @throws IllegalIndexName
     * @throws FileNotFoundException
     */
    public Index(String indexId, File dir, SearchManager searchMan) throws IllegalIndexName, FileNotFoundException {
        if (!isValidIndexName(indexId))
            throw new IllegalIndexName(indexId);
        if (dir == null || !dir.exists() || !dir.isDirectory())
            throw new FileNotFoundException("Cannot find index directory " + dir + ".");
        if (!dir.canRead() || !BlackLabIndex.isIndex(dir))
            throw new FileNotFoundException("Index directory " + dir + " is not an index or cannot be read.");

        this.id = indexId;
        this.dir = dir;
        this.searchMan = searchMan;

        // Opened on-demand
        this.index = null;
        this.indexer = null;

        shareWithUsersFile = new File(dir, SHARE_WITH_USERS_FILENAME);
        readShareWithUsersFile();
    }

    private void readShareWithUsersFile() {
        if (shareWithUsersFile.exists()) {
            try {
                shareWithUsers = FileUtils.readLines(shareWithUsersFile, "utf-8").stream().map(String::trim)
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            shareWithUsers = new ArrayList<>();
        }
    }

    private void writeShareWithUsersFile() {
        if (shareWithUsers.size() == 0) {
            // We don't want to share with anyone. Delete the share file if it exists.
            if (shareWithUsersFile.exists()) {
                if (!shareWithUsersFile.delete())
                    throw new RuntimeException("Could not delete share file: " + shareWithUsersFile);
            }
        } else {
            // (Over)write the share file with the current list of users to share with.
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(shareWithUsersFile),
                    StandardCharsets.UTF_8)) {
                writer.write(StringUtils.join(shareWithUsers, "\n"));
            } catch (IOException e) {
                throw new RuntimeException("Could not write share file", e);
            }
        }
    }

    public synchronized void setShareWithUsers(List<String> users) {
        shareWithUsers.clear();
        shareWithUsers.addAll(users);
        writeShareWithUsersFile();
    }

    public synchronized List<String> getShareWithUsers() {
        return Collections.unmodifiableList(shareWithUsers);
    }

    public String getId() {
        return id;
    }

    public File getDir() {
        return dir;
    }

    /**
     * Get the current BlackLabIndex backing this Index. This is not available while this
     * index is indexing new data.
     *
     * @return the currently opened BlackLabIndex
     * @throws InternalServerError when there was an error opening this index
     * @throws ServiceUnavailable when the index is in use.
     */
    // TODO index should not have references to it held for longer times outside of this class
    // (references should ideally never leave a synchronized(Index) block... [this might not be possible due to simultaneous searches]
    // (this is a large job)
    public synchronized BlackLabIndex blIndex() throws InternalServerError, ServiceUnavailable {
        try {
            openForSearching();
        } catch (IndexTooOld e) {
            throw BlsException.indexTooOld(e);
        }
        return index;
    }

    /**
     * Get the IndexMetadata for this Index. This could also be gotten from the
     * internal BlackLabIndex or Indexer inside this Index, but this always gets the most
     * up-to-date version.
     *
     * @return the index metadata
     * @throws InternalServerError if index couldn't be opened
     * @throws IndexTooOld if the index was too old to open by this versio of BlackLab
     */
    public synchronized IndexMetadata getIndexMetadata() throws InternalServerError, IndexTooOld {
        try {
            openForSearching();
        } catch (ServiceUnavailable e) {
            // swallow, we're apparently still busy indexing something,
            // this isn't a problem, we'll just use the Indexer's index to get the structure instead
        }

        if (this.index != null)
            return this.index.metadata();
        else if (this.indexer != null)
            return this.indexer.indexWriter().metadata();

        // This should literally never happen, after openForSearching either index or indexer must be set
        throw new RuntimeException(
                "Index in invalid state, openForSearching didn't throw unrecoverable error yet there is no BlackLabIndex and no Indexer");
    }

    public synchronized IndexStatus getStatus() throws BlsException {
        if (this.indexer != null && this.indexer.isOpen())
            return IndexStatus.INDEXING;

        return this.blIndex().isEmpty() ? IndexStatus.EMPTY : IndexStatus.AVAILABLE;
    }

    /**
     * Attempt to open this index in search mode. If this Index currently has an
     * open Indexer, checks whether the Indexer has finished (i.e. Indexer.close()
     * has been called), and cleans it up if so.
     *
     * @throws ServiceUnavailable if the index could not be opened due to currently
     *             ongoing indexing
     * @throws InternalServerError if there was some other error opening the index
     * @throws IndexTooOld if the index was too old to open by this version of BlackLab
     */
    private synchronized void openForSearching() throws ServiceUnavailable, InternalServerError, IndexTooOld {
        cleanupClosedIndexerOrThrow();

        if (this.index != null)
            return;

        //logger.debug("    Opening index '" + id + "', dir = " + dir);
        try {
            index = searchMan.blackLabInstance().open(this.dir);
            if (BlsCache.ENABLE_NEW_CACHE)
                index.setCache(searchMan.getBlackLabCache());
            //logger.debug("Done opening index '" + id + "'");
        } catch (IndexTooOld e) {
            throw e;
        } catch (ErrorOpeningIndex e) {
            throw new InternalServerError("Error opening index: " + dir, "INTERR_OPENING_INDEX", e);
        }
    }

    /**
     * Get an Indexer that can be used to add new data to this Index. Only one
     * indexer may be obtained at a time, meaning until the previous indexer can
     * be/has been cleaned up, ServiceUnavailable will be thrown. It is up to the
     * user to close the returned Indexer.
     *
     * Note that this will lock this index for searching until the Indexer has been
     * closed again.
     *
     * @return the indexer
     * @throws InternalServerError when the index cannot be opened for some reason
     * @throws ServiceUnavailable when there is already an Indexer on this Index
     *             that's still processing
     */
    public synchronized Indexer getIndexer() throws InternalServerError, ServiceUnavailable {
        cleanupClosedIndexerOrThrow();
        close(); // Close any BlackLabIndex that is still in search mode
        try {
            this.indexer = Indexer.openIndex(searchMan.blackLabInstance().openForWriting(this.dir, false), null);
            indexer.setUseThreads(true);
        } catch (Exception e) {
            throw new InternalServerError("Could not open index '" + id + "'", "INTERR_OPENING_INDEXWRITER", e);
        }

        return indexer;
    }

    /**
     * Gets the indexListener for the current Indexer. Returns null when this Index
     * is not currently Indexing.
     *
     * @return the listener, or null when there is no ongoing indexing.
     * @throws BlsException
     */
    public synchronized IndexListener getIndexerListener() throws BlsException {
        // Don't return inderListener for an Indexer that has been closed
        if (this.getStatus() != IndexStatus.INDEXING)
            return null;

        return this.indexer.listener();
    }

    /**
     * Close this index if it's currently open. Force closes any current Indexer.
     * Has no effect if the index was already closed.
     */
    public synchronized void close() {
        if (this.index != null) {
            this.index.close();
            this.index = null;
        }

        // if we're currently indexing, force close the indexer
        if (this.indexer != null && this.indexer.isOpen()) {
            this.indexer.close();
        }

        this.indexer = null;

//        searchMan.getCache().clearCacheForIndex(this.id);
        searchMan.getBlackLabCache().removeSearchesForIndex(this.index);
    }

    /**
     * Clean up the current Indexer (if any), provided close() has been called on
     * the Indexer. NOTE: we do not close the indexer ourselves on purpose (except
     * when Index.close() is called), instead we just check if it's been closed when
     * a BlackLabIndex or Indexer is requested.
     *
     * @throws ServiceUnavailable when the current indexer is still indexing
     */
    private synchronized void cleanupClosedIndexerOrThrow() throws ServiceUnavailable {
        if (this.indexer == null)
            return;

        if (this.indexer.isOpen())
            throw new ServiceUnavailable("Index '" + id + "' is currently indexing a file, please try again later.");

        // close() was already called on the indexer externally
        this.indexer = null;
    }

    //---------------------

    /**
     * three groups: username plus a ':' separator following it, this group is
     * non-capturing and optional -- (:?non capturing optional group ending with
     * ':')? inside that: the actual capturing of the username -- (:?(capturing):)?
     * beyond that, the indexname group, this is not optional so group 1 is always
     * the username, and group 2 is always the indexname
     */
    private static final Pattern PATT_INDEXID = Pattern.compile("^(?:([\\w\\Q-.!$&'()*+,;=@\\E]+):)?([\\w\\-]+)$");

    /**
     * Check the index name part (not the user id part, if any) of the specified
     * index name. Both indexName and indexIds may be used with this function.
     *
     * @param indexId the index id, possibly including user id prefix
     * @return whether or not the index name part is valid
     */
    public static boolean isValidIndexName(String indexId) {
        return PATT_INDEXID.matcher(indexId).matches();
    }

    /**
     * Check if this indexId is owned by a user
     *
     * @param indexId
     * @return true if this index is owned by a user
     * @throws IllegalIndexName
     */
    public static boolean isUserIndex(String indexId) throws IllegalIndexName {
        return getUserId(indexId) != null;
    }

    /**
     * Check if this indexId is owned by a user. Convenience version that doesn't
     * throw, as the indexId has already been verified as valid on construction.
     *
     * @return true if this index is owned by a user
     */

    public boolean isUserIndex() {
        return getUserId() != null;
    }

    /**
     * Get the user that owns this index. Returns null if this is not a user index.
     *
     * @param indexId
     * @return the username, or null if this is not a user index
     * @throws IllegalIndexName
     */
    public static String getUserId(String indexId) throws IllegalIndexName {
        Matcher m = PATT_INDEXID.matcher(indexId);
        if (!m.matches())
            throw new IllegalIndexName("Index name " + indexId + " contains illegal characters.");
        return m.group(1);
    }

    /**
     * A version of {@link Index#getUserId(String)} that doesn't throw, as the id
     * cannot be invalid.
     *
     * @return the username or null if this is not a user index.
     */
    public String getUserId() {
        Matcher m = PATT_INDEXID.matcher(this.getId());
        if (!m.matches())
            throw new RuntimeException();
        return m.group(1);
    }

    public boolean userMayRead(String userId) {
        // There are no restrictions on who can read non-user (public) indices
        if (!isUserIndex())
            return true;

        // Owner can always read their own index
        if (userId.equals(getUserId()))
            return true;

        // Any user the index is explicitly shared with can read it too
        return shareWithUsers.contains(userId);
    }

    /**
     * Get the name portion of the indexId.
     *
     * @param indexId
     * @return the indexname, never null
     * @throws IllegalIndexName
     */
    public static String getIndexName(String indexId) throws IllegalIndexName {
        Matcher m = PATT_INDEXID.matcher(indexId);
        if (!m.matches())
            throw new IllegalIndexName("Index name " + indexId + " contains illegal characters.");
        return m.group(2);
    }

    /**
     * A version of {@link Index#getIndexName(String)} that doesn't throw, as the id
     * cannot be invalid.
     *
     * @return the name of this index, never null.
     */
    public String getIndexName() {
        Matcher m = PATT_INDEXID.matcher(this.getId());
        if (!m.matches())
            throw new RuntimeException();
        return m.group(2);
    }

    /**
     * Given the base name for an index and a userId, get the corresponding unique
     * indexId for the index with that name for that user.
     *
     * @param indexName base name of the index, will be validated
     * @param userId optional user for which this index is being created, wil be
     *            validated if not null
     * @return the id that unique identifies the index with indexName owned by the
     *         given user
     * @throws IllegalIndexName when the result would be an illegal indexId
     */
    public static String getIndexId(String indexName, String userId) throws IllegalIndexName {
        String indexId = (userId == null) ? indexName : userId + ":" + indexName;
        if (!isValidIndexName(indexId))
            throw new IllegalIndexName("Index name " + indexId + " contains illegal characters.");

        return indexId;
    }

    //------------------------

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Index && ((Index) obj).getId().equals(this.getId())) {
            if (!((Index) obj).getDir().equals(this.getDir()))
                throw new RuntimeException("Index has same id but different directory");

            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
