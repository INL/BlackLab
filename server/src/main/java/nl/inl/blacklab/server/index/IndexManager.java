package nl.inl.blacklab.server.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.config.ConfigCorpus.TextDirection;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.JsonUtil;
import nl.inl.util.FileUtil;
import nl.inl.util.FileUtil.FileTask;

public class IndexManager {

	/**
	 * A file by this name is placed in user directories that could not be fully deleted,
	 * This can happen under windows when some of the files are still open in some memory-maps (despite .close() having been called)
	 * The files cannot be deleted until the streams and files are garbage-collected.
	 * Since there is no deterministic way to trigger a gc run, we detect this situation and mark the directory for future deletion.
	 */
	private static final String PENDING_DELETION_FILE_MARKER = ".markedfordeletion";

	private static final Logger logger = LogManager.getLogger(IndexManager.class);

	private static final int MAX_USER_INDICES = 10;

	private SearchManager searchMan;

	/** Configured index collections directories */
	private List<File> collectionsDirs;

	/**
	 * Logged-in users will have their own private collections dir. This is the
	 * parent of those dirs.
	 */
	private File userCollectionsDir;

	/**
	 * Manages the loaded user document formats and exposes them to BlackLab-core for use.
	 */
	private DocIndexerFactoryUserFormats userFormatManager;

    private Map<String, Index> indices = new HashMap<>();

    // properties is temp/blacklab-server.json
	public IndexManager(SearchManager searchMan, JsonNode properties) throws ConfigurationException {
		this.searchMan = searchMan;

		if (properties.has("indices")) {
			JsonNode indicesMap = properties.get("indices");
			Iterator<Entry<String, JsonNode>> it = indicesMap.fields();
			while (it.hasNext()) {
			    Entry<String, JsonNode> entry = it.next();
			    String indexName = entry.getKey();
				JsonNode indexConfig = entry.getValue();

				try {
					File dir = JsonUtil.getFileProp(indexConfig, "dir", null);
					Index index = new Index(indexName, dir, searchMan.getCache());

					if (indexConfig.has("pid")) {
						// Should be specified in index metadata now, not in
						// blacklab-server.json.
						logger.warn("blacklab-server.json specifies 'pid' property for index '" + indexName +
								"'; this setting should not be in blacklab-server.json but in the blacklab index metadata! (as 'pidField')");
						logger.warn("For now this index will still use the field from blacklab-server.json if it isn't defined in the index metadata, but this will change in the future.");

						index.setDeprecatedPidFieldProperty(indexConfig.get("pid").textValue());
					}

					if (indexConfig.has("mayViewContent")) {
						logger.warn("blacklab-server.json specifies 'mayViewContent' property for index'" + indexName +
								"'; this setting should not be in blacklab-server.json but in the blacklab index metadata! (as 'contentViewable')");
						logger.warn("For now this value can still be used to explicitly disable content viewing, but not to enable it if the index itself does not allow viewing.");
						index.setDeprecatedMayViewContentsProperty(indexConfig.get("mayViewContent").booleanValue());
					}

					indices.put(indexName, index);
				} catch (FileNotFoundException | IllegalIndexName e) {
					logger.error("Error opening index '"+indexName+"'; " + e.getMessage());
				}
			}
		}

		// Collections, these are lazily loaded, and additions/removals within them should be detected.
		collectionsDirs = new ArrayList<>();
		if (properties.has("indexCollections")) {
            logger.debug("Scanning indexCollections...");
			for (JsonNode collectionNode : properties.get("indexCollections")) {
				File collectionDir = new File(collectionNode.textValue());
				if (collectionDir.canRead()) {
		            logger.debug("Found index collection dir: " + collectionDir);
					collectionsDirs.add(collectionDir);
				} else
					logger.warn("Configured collection not found or not readable: " + collectionDir);
			}
		} else {
		    logger.debug("No indexCollections setting found.");
		}

		// User collections dir, these are like collections, but within a user's directory
		this.userCollectionsDir = JsonUtil.getFileProp(properties, "userCollectionsDir", null);
		if (userCollectionsDir == null || !userCollectionsDir.canRead()) {
			logger.warn("Configured user collections not found or not readable: " + userCollectionsDir);
			userCollectionsDir = null;
		} else {
			userFormatManager = new DocIndexerFactoryUserFormats(userCollectionsDir);
			DocumentFormats.registerFactory(userFormatManager);
		}

		if (indices.isEmpty() && collectionsDirs.isEmpty() && userCollectionsDir == null) {
			throw new ConfigurationException(
				"Configuration error: no index locations found. Create " +
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
	 * Does the specified index exist?
	 * Attempts to load any new public indices before returning (if this is a user index, attempts to load any new indices for this user).
	 *
	 * @param indexId the index we want to check for
	 * @return true iff the index exists
	 * @throws BlsException
	 */
	public synchronized boolean indexExists(String indexId) throws BlsException {
        try {
            cleanupRemovedIndices();
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
	 * Indices may only be created by a logged-in user in his own private area.
	 * The index name is strictly validated, disallowing any weird input.
	 *
	 * @param indexId
	 *            the index name, including user prefix
	 * @param displayName
	 * @param documentFormatId the document format identifier (e.g. tei, folia, ..)
	 * @throws BlsException
	 *             if we're not allowed to create the index for whatever reason
	 * @throws IOException
	 *             if creation failed unexpectedly
	 */
	public synchronized void createIndex(String indexId, String displayName, String documentFormatId) throws BlsException,
			IOException {
		if (!DocumentFormats.isSupported(documentFormatId))
			throw new BadRequest("FORMAT_NOT_FOUND", "Unknown format: " + documentFormatId);
		if (!Index.isUserIndex(indexId))
			throw new NotAuthorized("Can only create private indices.");
		if (!Index.isValidIndexName(indexId))
			throw new IllegalIndexName(indexId);
		if (indexExists(indexId))
			throw new BadRequest("INDEX_ALREADY_EXISTS",
					"Could not create index. Index already exists.");


		String userId = Index.getUserId(indexId);
		String indexName = Index.getIndexName(indexId);

		if (!canCreateIndex(userId))
			throw new BadRequest("CANNOT_CREATE_INDEX ",
					"Could not create index. You already have the maximum of "
							+ IndexManager.MAX_USER_INDICES + " indices.");

		if (userCollectionsDir == null)
			throw new BadRequest("CANNOT_CREATE_INDEX ", "Could not create index. The server is not configured with support for user content.");

		File userDir = getUserCollectionDir(userId);
		if (userDir == null || !userDir.canWrite())
			throw new InternalServerError("Could not create index. Cannot write in user dir: " + userDir, 16);

		File indexDir = new File(userDir, indexName);

		// TODO this should be handled by Index
		if (isPendingDeletion(indexDir)) {
			// Don't let any deletion markers linger around (when index used to exist and couldn't be fully deleted)
			BlsUtils.delTree(indexDir);
		}
		boolean contentViewable = true; // user may view his own private corpus documents
		Searcher searcher = Searcher.createIndex(indexDir, displayName, documentFormatId, contentViewable, TextDirection.LEFT_TO_RIGHT);
		searcher.close();

		indices.put(indexId, new Index(indexId, indexDir, this.searchMan.getCache()));
	}

	public boolean canCreateIndex(String userId) {
		return userCollectionsDir != null && getAvailablePrivateIndices(userId).size() < IndexManager.MAX_USER_INDICES;
	}

	/**
	 * Delete a user index.
	 *
	 * Only user indices are deletable. The owner must be logged in. The index
	 * name is strictly validated, disallowing any weird input. Many other
	 * checks are done to root out all kinds of special cases.
	 *
	 * @param indexId the index id, including user prefix
	 * @throws NotAuthorized if this is not a user index
	 * @throws IndexNotFound if no such index exists
	 * @throws InternalServerError if the index is in an invalid state
	 * @throws IllegalIndexName
	 */
	public synchronized void deleteUserIndex(String indexId) throws NotAuthorized, IndexNotFound, InternalServerError, IllegalIndexName  {
		if (!Index.isUserIndex(indexId))
			throw new NotAuthorized("Can only delete private indices.");

		Index index = getIndex(indexId);

		File indexDir = index.getDir();
		File userDir = getUserCollectionDir(index.getUserId());

		// Generally these should never happen as they would have been triggered when the Index was first loaded
		// But, it can't hurt to be certain
		if (!indexDir.isDirectory())
			throw new InternalServerError("Could not delete index. Not an index.", 17);
		if (!userDir.canWrite() || !indexDir.canWrite())
			throw new InternalServerError("Could not delete index. Check file permissions.", 18);
		if (!indexDir.getAbsoluteFile().getParentFile().equals(userDir))  // Yes, we're paranoid..
			throw new InternalServerError("Could not delete index. Not found in user dir.", 19);
		if (!Searcher.isIndex(indexDir)) { // ..but are we paranoid enough?
			throw new InternalServerError("Could not delete index. Not a BlackLab index.", 20);
		}

		// Don't follow symlinks
		try {
			if (BlsUtils.isSymlink(indexDir)) {
				throw new InternalServerError("Could not delete index. Is a symlink.", 21);
			}
		} catch (IOException e1) {
			throw new InternalServerError(13);
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
			throw new InternalServerError("Could not delete index. Can't delete all files/dirs.", 22);
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
	 * Get the Index with this id.
	 * Attempts to load public indices (if this index is a user index, additionally tries to load the user's indices).
	 *
	 * @param indexId
	 * @return the Index, never null
	 * @throws IndexNotFound when the index could not be found
	 */
	public synchronized Index getIndex(String indexId) throws IndexNotFound {
		try {
			cleanupRemovedIndices();
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
	 * Get all public indices plus all indices owned by this user.
	 * Attempts to load any new public indices and indices owned by this user.
	 *
	 * @param userId the user
	 * @return the list of indices
	 */
	public synchronized List<Index> getAllAvailableIndices(String userId) {
		List<Index> availableIndices = new ArrayList<>();
		availableIndices.addAll(getAvailablePrivateIndices(userId));
		availableIndices.addAll(getAvailablePublicIndices());

		Collections.sort(availableIndices, Index.COMPARATOR);
		return availableIndices;
	}

	/**
	 * Return the list of private indices available for searching.
	 * Attempts to load any new indices for this user.
	 *
	 * @param userId the user
	 * @return the list of indices
	 */
	public synchronized Collection<Index> getAvailablePrivateIndices(String userId) {
		if (userId == null)
			return Collections.emptyList();

		cleanupRemovedIndices();
		loadUserIndices(userId);

		Set<Index> availableIndices = new HashSet<>();
		for (Index i : indices.values()) {
			if (userId.equals(i.getUserId()))
				availableIndices.add(i);
		}

		return availableIndices;
	}

	/**
	 * Return the list of public indices available for searching.
	 * Attempts to load any new public indices.
	 *
	 * @return the list of indices
	 */
	public synchronized Collection<Index> getAvailablePublicIndices() {
		Set<Index> availableIndices = new HashSet<>();

		cleanupRemovedIndices();
		loadPublicIndices();
		for (Index i : indices.values()) {
			if (!i.isUserIndex())
				availableIndices.add(i);
		}

		return availableIndices;
	}

	/**
	 * Find all indices within our collection directories, and add them to the {@link IndexManager#indices} list.
	 * Indices that are already loaded are skipped.
	 */
	private synchronized void loadPublicIndices() {
		if (collectionsDirs == null)
			return;

		synchronized (indices) {
		    logger.debug("Looking for indices in collectionsDirs...");
			for (File collection : collectionsDirs) {
	            logger.debug("  Scanning collectionsDir: " + collection);
				for (File subDir : FileUtils.listFilesAndDirs(collection, FalseFileFilter.FALSE, TrueFileFilter.INSTANCE /* can't filter on name yet, or it will only recurse into dirs with that name */)) {
					if (/*!subDir.getName().equals("index") ||*/ !subDir.canRead() || !Searcher.isIndex(subDir)) {
		                if (subDir.getParentFile().equals(collection)) {
		                    logger.debug("  Direct subdir of collection dir is not an index or cannot read: " + subDir);
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
                        logger.warn("Replacing this with the parent directory name (" + indexName + "), but note that this behaviour is deprecated.");
					}
					if (indices.containsKey(indexName))
						continue;

					try {
					    logger.debug("Index found: " + indexName + " (" + subDir + ")");
						indices.put(indexName, new Index(indexName, subDir, searchMan.getCache()));
					} catch (Exception e) {
						logger.info("Error while loading index " + indexName + " at location " + subDir + "; " + e.getMessage());
					}
				}
			}
		}
	}

	/**
	 * Find and add all indices within this user's private directory, and add them to the {@link IndexManager#indices} list.
	 * Indices that are already loaded are skipped.
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
		for (File f : userDir.listFiles(BlsUtils.readableDirFilter)) {
			if (isPendingDeletion(f)) {
				BlsUtils.delTree(f);
				if (f.canRead())
					markForDeletion(f); // Deleting didn't work (yet)
				continue;
			}

			if (!f.canRead() || !Searcher.isIndex(f))
				continue;

			try {
				String indexId = Index.getIndexId(f.getName(), userId);
				if (indices.containsKey(indexId))
					continue;

				indices.put(indexId, new Index(indexId, f, searchMan.getCache()));
			} catch (Exception e) {
				logger.info("Error while loading index " + f.getName() + " at location " + f + "; " + e.getMessage());
			}
		}
	}

	/**
	 * Checks all indices to see if their directories are still readable, and removes them if this is not the case.
	 * This can happen when the index is deleted on-disk while we're running.
	 * This feature is explicitly supported.
	 */
	private synchronized void cleanupRemovedIndices() {
		List<String> removedIds = new ArrayList<>();
		for (Index i : indices.values()) {
			if (!i.getDir().canRead()) {
				removedIds.add(i.getId());
			}
		}
		for (String id : removedIds) {
			indices.remove(id).close();
		}
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
	 * Note that this will return null if no userCollectionsDir has been set configured, as there is no place to store/read the formats.
	 *
	 * @return The user format manager/DocIndexerFactory.
	 */
    public DocIndexerFactoryUserFormats getUserFormatManager() {
    	return userFormatManager;
    }

    /**
	 * @deprecated use {@link Index#getSearcher()}
	 */
	@SuppressWarnings("javadoc")
	@Deprecated
	public Searcher getSearcher(String indexId) throws IndexNotFound, InternalServerError, ServiceUnavailable {
		return getIndex(indexId).getSearcher();
	}

	/**
 	 * @deprecated use {@link Index#getStatus()}
	 */
	@SuppressWarnings("javadoc")
	@Deprecated
	public Index.IndexStatus getIndexStatus(String indexId) throws IndexNotFound {
		return getIndex(indexId).getStatus();
	}
}