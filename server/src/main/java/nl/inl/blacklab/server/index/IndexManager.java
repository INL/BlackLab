package nl.inl.blacklab.server.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.util.FileUtil;
import nl.inl.util.FileUtil.FileTask;

public class IndexManager {

    /**
     * A file by this name is placed in user directories that could not be fully
     * deleted, This can happen under windows when some of the files are still open
     * in some memory-maps (despite .close() having been called) The files cannot be
     * deleted until the streams and files are garbage-collected. Since there is no
     * deterministic way to trigger a gc run, we detect this situation and mark the
     * directory for future deletion.
     */
    private static final String PENDING_DELETION_FILE_MARKER = ".markedfordeletion";

    /**
     * The frequency at which we check for removed indices in the file system.
     */
    private static final int REMOVED_INDICES_MONITOR_CHECK_IN_MS = 1000;

    private static final Logger logger = LogManager.getLogger(IndexManager.class);

    private SearchManager searchMan;

    /** Configured index collections directories */
    private List<File> collectionsDirs;

    /**
     * Logged-in users will have their own private collections dir. This is the
     * parent of those dirs.
     */
    private File userCollectionsDir;

    /**
     * Manages the loaded user document formats and exposes them to BlackLab-core
     * for use.
     */
    private DocIndexerFactoryUserFormats userFormatManager;

    private Map<String, Index> indices = new HashMap<>();

    public IndexManager(SearchManager searchMan, BLSConfig blsConfig) throws ConfigurationException {
        this.searchMan = searchMan;

        // List of index collections dirs (and/or single index dirs)
        List<String> indexes = blsConfig.getIndexLocations();
        collectionsDirs = new ArrayList<>();
        for (String indexPath: indexes) {
            File indexDir = new File(indexPath);
            if (!indexDir.exists()) {
                logger.warn("indexes section contains entry that doesn't exist: " + indexDir);
                continue;
            }
            if (!indexDir.canRead()) {
                logger.warn("indexes section contains unreadable entry: " + indexDir);
                continue;
            }

            // Is this a single index, or a collection of indexes?
            if (BlackLabIndex.isIndex(indexDir)) {
                // Single index.
                logger.debug("Single index found: " + indexDir);
                Index index;
                try {
                    index = new Index(indexDir.getName(), indexDir, searchMan);
                    indices.put(indexDir.getName(), index);
                } catch (FileNotFoundException | IllegalIndexName e) {
                    logger.error("Error opening index '" + indexDir + "'; " + e.getMessage());
                }
            } else {
                // Collection of indices, probably..?
                logger.debug("Index collection dir found: " + indexDir);
                collectionsDirs.add(indexDir);
            }
        }

        // User collections dir; these are like collections, but within a user's directory
        userCollectionsDir = null;
        if (!StringUtils.isEmpty(blsConfig.getUserIndexes())) {
            File userIndexesDir = new File(blsConfig.getUserIndexes());
            if (!userIndexesDir.exists())
                logger.warn("Configured user collections does not exist: " + userIndexesDir);
            else if (!userIndexesDir.canRead())
                logger.warn("Configured user collections unreadable: " + userIndexesDir);
            else {
                userCollectionsDir = userIndexesDir;
                userFormatManager = new DocIndexerFactoryUserFormats(userCollectionsDir);
                DocumentFormats.registerFactory(userFormatManager);
            }
        }

        checkAnyIndexesAvailable();
        List<File> allDirs = new ArrayList<>(collectionsDirs);
        allDirs.add(userCollectionsDir);
        try {
            startRemovedIndicesMonitor(allDirs, REMOVED_INDICES_MONITOR_CHECK_IN_MS);
        } catch (Exception ex) {
            throw  BlackLabRuntimeException.wrap(ex);
        }
    }

    private void checkAnyIndexesAvailable() throws ConfigurationException {
        if (indices.isEmpty() && collectionsDirs.isEmpty() && userCollectionsDir == null) {
            throw new ConfigurationException(
                    "Configuration error: no readable index locations found. Create " +
                            "/etc/blacklab/blacklab-server.json containing at least the following:\n" +
                            "{\n" +
                            "  \"indexCollections\": [\n" +
                            "    \"/dir/containing/indices\"\n" +
                            "  ]\n" +
                            "}");
        }
    }

    /**
     * Return the specified user's collection dir.
     *
     * @param userId the user
     * @return the user's collection dir, or null if it can't be read or created
     */
    private File getUserCollectionDir(String userId) {
        if (userCollectionsDir == null || userId == null || userId.isEmpty())
            return null;
        File dir = new File(userCollectionsDir, User.getUserDirNameFromId(userId));
        if (!dir.exists())
            dir.mkdir();
        if (!dir.canRead()) {
            logger.error("Cannot read collections dir for user: " + dir);
            logger.error("(userCollectionsDir = " + userCollectionsDir);
            return null;
        }
        return dir;
    }

    /**
     * Does the specified index exist? Attempts to load any new public indices
     * before returning (if this is a user index, attempts to load any new indices
     * for this user).
     *
     * @param indexId the index we want to check for
     * @return true iff the index exists
     * @throws BlsException
     */
    public synchronized boolean indexExists(String indexId) throws BlsException {
        try {
            if (!indices.containsKey(indexId)) {
                if (Index.isUserIndex(indexId))
                    loadUserIndices(Index.getUserId(indexId));
                else
                    loadPublicIndices();
            }
            return indices.containsKey(indexId);
        } catch (IllegalIndexName e) {
            throw new IndexNotFound(e.getMessage());
        }
    }

    /**
     * Create an empty user index.
     *
     * Indices may only be created by a logged-in user in his own private area. The
     * index name is strictly validated, disallowing any weird input.
     *
     * @param user the logged-in user
     * @param indexId the index name, including user prefix
     * @param displayName
     * @param formatIdentifier the document format identifier (e.g. tei, folia, ..).
     *            See {@link DocumentFormats}
     * @throws BlsException if we're not allowed to create the index for whatever
     *             reason
     * @throws ErrorOpeningIndex if creation failed unexpectedly
     */
    public synchronized void createIndex(User user, String indexId, String displayName, String formatIdentifier)
            throws BlsException, ErrorOpeningIndex {
        if (!DocumentFormats.isSupported(formatIdentifier))
            throw new BadRequest("FORMAT_NOT_FOUND", "Unknown format: " + formatIdentifier);
        if (!Index.isUserIndex(indexId))
            throw new NotAuthorized("Can only create private indices.");
        if (!Index.isValidIndexName(indexId))
            throw new IllegalIndexName(indexId);
        if (indexExists(indexId))
            throw new BadRequest("INDEX_ALREADY_EXISTS",
                    "Could not create index. Index already exists.");

        String userId = Index.getUserId(indexId);
        if (!userId.equals(user.getUserId()) && !user.isSuperuser())
            throw new NotAuthorized("Could not create index. Can only create your own private indices.");
        String indexName = Index.getIndexName(indexId);

        if (userCollectionsDir == null)
            throw new BadRequest("CANNOT_CREATE_INDEX ",
                "Could not create index. The server is not configured with support for user content.");

        int maxNumberOfIndices = searchMan.config().getIndexing().getMaxNumberOfIndicesPerUser();
        if (!canCreateIndex(user))
            throw new BadRequest("CANNOT_CREATE_INDEX ",
                    "Could not create index. You already have the maximum of "
                            + maxNumberOfIndices + " indices.");


        File userDir = getUserCollectionDir(userId);
        if (userDir == null || !userDir.canWrite())
            throw new InternalServerError("Could not create index. Cannot write in user dir: " + userDir, "CANNOT_WRITE_USER_DIR");

        File indexDir = new File(userDir, indexName);

        // TODO this should be handled by Index
        if (isPendingDeletion(indexDir)) {
            // Don't let any deletion markers linger around (when index used to exist and couldn't be fully deleted)
            BlsUtils.delTree(indexDir);
        }
        boolean contentViewable = true; // user may view his own private corpus documents
        Format format = DocumentFormats.getFormat(formatIdentifier);
        ConfigInputFormat config = format == null ? null : format.getConfig();
        try (BlackLabIndexWriter indexWriter = searchMan.blackLabInstance().create(indexDir, config)) {
            IndexMetadataWriter indexMetadata = indexWriter.metadata();
            if (!StringUtils.isEmpty(displayName))
                indexMetadata.setDisplayName(displayName);
            if (config != null && config.getName() != null)
                indexMetadata.setDocumentFormat(config.getName());
            else if (!StringUtils.isEmpty(formatIdentifier)) {
                indexMetadata.setDocumentFormat(formatIdentifier);
            }
            indexMetadata.setContentViewable(contentViewable);
            indexMetadata.save();
        }

        try {
            logger.debug("Created index: " + indexName + " (" + indexDir + ")");
            indices.put(indexId, new Index(indexId, indexDir, this.searchMan));
        } catch (FileNotFoundException e) {
            throw new ErrorOpeningIndex("Could not open index: " + indexDir, e);
        }
    }

    public boolean canCreateIndex(User user) {
        int maxNumberOfIndices = searchMan.config().getIndexing().getMaxNumberOfIndicesPerUser();

        // No limit on the number of indices
        if (maxNumberOfIndices ==  -1) {
            return true;
        }
        return userCollectionsDir != null &&
            (getAvailablePrivateIndices(user.getUserId()).size() <= maxNumberOfIndices || user.isSuperuser());
    }

    /**
     * Delete a user index.
     *
     * Only user indices are deletable. The owner must be logged in. The index name
     * is strictly validated, disallowing any weird input. Many other checks are
     * done to root out all kinds of special cases.
     *
     * @param indexId the index id, including user prefix
     * @throws NotAuthorized if this is not a user index
     * @throws IndexNotFound if no such index exists
     * @throws InternalServerError if the index is in an invalid state
     * @throws IllegalIndexName
     */
    public synchronized void deleteUserIndex(String indexId)
            throws NotAuthorized, IndexNotFound, InternalServerError, IllegalIndexName {
        if (!Index.isUserIndex(indexId))
            throw new NotAuthorized("Can only delete private indices.");

        Index index = getIndex(indexId);

        File indexDir = index.getDir();
        File userDir = getUserCollectionDir(index.getUserId());

        // Generally these should never happen as they would have been triggered when the Index was first loaded
        // But, it can't hurt to be certain
        if (!indexDir.isDirectory())
            throw new InternalServerError("Could not delete index. Not an index.", "INTERR_DELETING_INDEX1");
        if (!userDir.canWrite() || !indexDir.canWrite())
            throw new InternalServerError("Could not delete index. Check file permissions.", "INTERR_DELETING_INDEX2");
        if (!indexDir.getAbsoluteFile().getParentFile().equals(userDir)) // Yes, we're paranoid..
            throw new InternalServerError("Could not delete index. Not found in user dir.", "INTERR_DELETING_INDEX3");
        if (!BlackLabIndex.isIndex(indexDir)) { // ..but are we paranoid enough?
            throw new InternalServerError("Could not delete index. Not a BlackLab index.", "INTERR_DELETING_INDEX4");
        }

        // Don't follow symlinks
        try {
            if (BlsUtils.isSymlink(indexDir)) {
                throw new InternalServerError("Could not delete index. Is a symlink.", "INTERR_DELETING_INDEX5");
            }
        } catch (IOException e1) {
            throw new InternalServerError("INTERR_DELETING_INDEX6");
        }

        // Can we even delete the whole tree? If not, don't even try.
        try {
            FileUtil.processTree(indexDir, new FileTask() {
                @Override
                public void process(File f) {
                    if (!f.canWrite())
                        throw new RuntimeException("Cannot delete " + f);
                }
            });
        } catch (Exception e) {
            throw new InternalServerError("Could not delete index. Can't delete all files/dirs.", "INTERR_DELETING_INDEX7");
        }

        // Everything seems ok. Delete the index.
        logger.debug("Deleting user index " + index.getId());
        indices.remove(indexId);
        index.close();

        // Cant guarantee this will work, but may as well try
        System.gc();
        System.runFinalization();

        BlsUtils.delTree(indexDir);
        // didn't fully delete, this can happen under windows when some memmapped buffers haven't been gc'd yet
        // This is a system bug, not something we can do anything about, the gc first needs to clean up all references to those maps
        // Mark the directory and attempt to delete it next time we come across it
        if (indexDir.canRead())
            markForDeletion(indexDir);
    }

    /**
     * Get the Index with this id. Attempts to load public indices (if this index is
     * a user index, additionally tries to load the user's indices).
     *
     * @param indexId
     * @return the Index, never null
     * @throws IndexNotFound when the index could not be found
     */
    public synchronized Index getIndex(String indexId) throws IndexNotFound {
        try {
            if (!indices.containsKey(indexId)) {
                if (Index.isUserIndex(indexId))
                    loadUserIndices(Index.getUserId(indexId));
                else
                    loadPublicIndices();
            }

            Index index = indices.get(indexId);
            if (index == null)
                throw new IndexNotFound(indexId);

            return index;
        } catch (IllegalIndexName e) {
            throw new IndexNotFound(e.getMessage());
        }
    }

    /**
     * Get all public indices plus all indices owned by this user. Attempts to load
     * any new public indices and indices owned by this user.
     *
     * @param userId the user
     * @return the list of indices
     */
    public synchronized List<Index> getAllAvailableIndices(String userId) {
        List<Index> availableIndices = new ArrayList<>();
        availableIndices.addAll(getAvailablePrivateIndices(userId));
        availableIndices.addAll(getAvailablePublicIndices());

        availableIndices.sort(Index.COMPARATOR);
        return availableIndices;
    }

    /**
     * Return the list of private indices available for searching. Attempts to load
     * any new indices for this user.
     *
     * @param userId the user
     * @return the list of indices
     */
    public synchronized Collection<Index> getAvailablePrivateIndices(String userId) {
        if (userId == null)
            return Collections.emptyList();

        loadUserIndices(userId);

        Set<Index> availableIndices = new HashSet<>();
        for (Index i : indices.values()) {
            if (userId.equals(i.getUserId()))
                availableIndices.add(i);
        }

        return availableIndices;
    }

    /**
     * Return the list of public indices available for searching. Attempts to load
     * any new public indices.
     *
     * @return the list of indices
     */
    public synchronized Collection<Index> getAvailablePublicIndices() {
        Set<Index> availableIndices = new HashSet<>();

        loadPublicIndices();
        for (Index i : indices.values()) {
            if (!i.isUserIndex())
                availableIndices.add(i);
        }

        return availableIndices;
    }

    /**
     * Find all indices within our collection directories, and add them to the
     * {@link IndexManager#indices} list. Indices that are already loaded are
     * skipped.
     */
    private synchronized void loadPublicIndices() {
        if (collectionsDirs == null)
            return;

        synchronized (indices) {
            logger.debug("Looking for indices in collectionsDirs...");
            for (File collection : collectionsDirs) {
                logger.debug("Scanning collectionsDir: " + collection);
                // A file filter that accepts all directories (and files) except the userCollectionsDir,
                // so if the userCollectionsDir is inside a collectionsDir, it is not suddenly made public
                IOFileFilter notUserDirFilter = new IOFileFilter() {
                    @Override
                    public boolean accept(File pathName) {
                        try {
                            if (userCollectionsDir == null)
                                return true;
                            return !pathName.getCanonicalPath().equals(userCollectionsDir.getCanonicalPath());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public boolean accept(File pathName, String fileName) {
                        return accept(pathName);
                    }
                };
                for (File subDir : FileUtils.listFilesAndDirs(collection, FalseFileFilter.FALSE,
                        notUserDirFilter /* can't filter on name yet, or it will only recurse into dirs with that name */)) {
                    if (/*!subDir.getName().equals("index") ||*/ !subDir.canRead() || !BlackLabIndex.isIndex(subDir)) {
                        if (subDir.getParentFile().equals(collection)) {
                            if (!subDir.canRead())
                                logger.debug("  Cannot read direct subdir of collection dir: " + subDir);
                            else
                                logger.debug("  Direct subdir of collection dir not recognized as an index: " + subDir);
                        }
                        continue;
                    }

                    String indexName = subDir.getName();
                    if (indexName.equals("index")) {
                        // Not a very useful name; the parent directory usually contains the index name in this case
                        indexName = subDir.getAbsoluteFile().getParentFile().getName();
                        if (indices.containsKey(indexName))
                            continue;

                        logger.warn("Found index directory named 'index': " + subDir);
                        logger.warn("Replacing this with the parent directory name (" + indexName
                                + "), but note that this behaviour is deprecated.");
                    }
                    if (indices.containsKey(indexName))
                        continue;

                    try {
                        logger.debug("Index found: " + indexName + " (" + subDir + ")");
                        indices.put(indexName, new Index(indexName, subDir, searchMan));
                    } catch (Exception e) {
                        logger.info("Error while loading index " + indexName + " at location " + subDir + "; "
                                + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Find and add all indices within this user's private directory, and add them
     * to the {@link IndexManager#indices} list. Indices that are already loaded are
     * skipped.
     *
     * @param userId the user for which to load indices
     */
    private synchronized void loadUserIndices(String userId) {
        File userDir = getUserCollectionDir(userId);
        if (userDir == null)
            return;

        /*
         * User indices are stored as a flat list of directories inside the user's private directory like so:
         * 	userDir
         * 		indexDir1
         * 		indexDir2
         * 		...
         *
         * The name of the directory is the UNPREFIXED name of the index, so we need to take care to concatenate the userId and indexName
         * so the index can be recognised as a private index.
         */
        logger.debug("Scanning userDir: " + userDir);
        for (File f : userDir.listFiles(BlsUtils.readableDirFilter)) {
            if (isPendingDeletion(f)) {
                BlsUtils.delTree(f);
                if (f.canRead())
                    markForDeletion(f); // Deleting didn't work (yet)
                continue;
            }

            if (!f.canRead() || !BlackLabIndex.isIndex(f))
                continue;

            try {
                String indexId = Index.getIndexId(f.getName(), userId);
                if (indices.containsKey(indexId))
                    continue;

                logger.debug("User index found: " + indexId + " (" + f + ")");
                indices.put(indexId, new Index(indexId, f, searchMan));
            } catch (Exception e) {
                logger.info("Error while loading index " + f.getName() + " at location " + f + "; " + e.getMessage());
            }
        }
    }

    /**
     * Starts a monitor to remove references to indices whose physical file was removed
     * @param directories to monitor
     * @param pollingIntervalInMs how ofter to monitor the directories
     * @return the monitor
     * @throws Exception
     */
    public FileAlterationMonitor startRemovedIndicesMonitor(List<File> directories, long pollingIntervalInMs) throws Exception {
        logger.info("Installing index removal watcher on: {}", directories);
        FileAlterationMonitor monitor = new FileAlterationMonitor(pollingIntervalInMs);
        List<FileAlterationObserver> observers = directories.stream()
            .map(FileAlterationObserver::new)
            .collect(Collectors.toList());
        FileAlterationListenerAdaptor listener = new FileAlterationListenerAdaptor() {
            @Override
            public void onDirectoryDelete(File directory) {
                logger.info("Directory deleted: {}", directory.getAbsolutePath());
                synchronized (IndexManager.this) {
                    Optional<Index> indexToDelete = indices.values().stream()
                        .filter(i -> i.getDir().equals(directory))
                        .findFirst();
                    indexToDelete.ifPresent(i -> {
                        logger.info("Deleting index {}, {}", i.getId(), i.getDir().getAbsolutePath());
                        indices.remove(i.getId()).close();
                    });
                }
            }
        };
        observers.forEach(o -> {
            o.addListener(listener);
            monitor.addObserver(o);
        });
        monitor.start();
        return monitor;
    }

    private static void markForDeletion(File directory) {
        try {
            File deletionMarker = new File(directory, PENDING_DELETION_FILE_MARKER);
            deletionMarker.createNewFile();
        } catch (IOException e) {
            logger.error("Cannot mark directory for future deletion: " + e.getMessage());
        }
    }

    private static boolean isPendingDeletion(File directory) {
        File deletionMarker = new File(directory, PENDING_DELETION_FILE_MARKER);
        return (deletionMarker.exists() && deletionMarker.canRead());
    }

    /**
     * Note that this will return null if no userCollectionsDir has been set
     * configured, as there is no place to store/read the formats.
     *
     * @return The user format manager/DocIndexerFactory.
     */
    public DocIndexerFactoryUserFormats getUserFormatManager() {
        return userFormatManager;
    }
}
