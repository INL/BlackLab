package nl.inl.blacklab.server.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.JsonUtil;
import nl.inl.util.FileUtil;
import nl.inl.util.FileUtil.FileTask;

public class IndexManager {
	private static final Logger logger = LogManager.getLogger(IndexManager.class);

	private static final int MAX_USER_INDICES = 10;

	private SearchManager searchMan;

	/** Our current set of indices (with dir and mayViewContent setting) */
	private Map<String, IndexParam> indexParam;

	/**
	 * The status of each index, i.e. "available" or "indexing". If no status is
	 * stored here, the status is "available".
	 */
	Map<String, String> indexStatus;

	/** The Searcher objects, one for each of the indices we can search. */
	private Map<String, Searcher> searchers = new HashMap<>();

	/** Configured index collections directories */
	private List<File> collectionsDirs;

	/**
	 * Logged-in users will have their own private collections dir. This is the
	 * parent of that dir.
	 */
	private File userCollectionsDir;

	private SearchCache cache;

	public IndexManager(SearchManager searchMan, JSONObject properties) throws ConfigurationException {
		this.searchMan = searchMan;
		this.cache = searchMan.getCache();
		indexParam = new HashMap<>();
		indexStatus = new HashMap<>();

		boolean indicesFound = false;
		if (properties.has("indices")) {
			JSONObject indicesMap = properties.getJSONObject("indices");
			Iterator<?> it = indicesMap.keys();
			while (it.hasNext()) {
				String indexName = (String) it.next();
				JSONObject indexConfig = indicesMap.getJSONObject(indexName);

				File dir = JsonUtil.getFileProp(indexConfig, "dir", null);
				if (dir == null || !dir.canRead()) {
					logger.error("Index directory for index '" + indexName
							+ "' does not exist or cannot be read: " + dir);
					continue;
				}
				if (!Searcher.isIndex(dir)) {
					logger.warn("Directory " + dir + " does not contain a BlackLab index.");
					continue;
				}

				String pid = JsonUtil.getProperty(indexConfig, "pid", "");
				if (pid.length() != 0) {
					// Should be specified in index metadata now, not in
					// blacklab-server.json.
					logger.error("blacklab-server.json specifies 'pid' property for index '"
							+ indexName
							+ "'; this setting should not be in blacklab-server.json but in the blacklab index metadata!");
				}

				// Does the settings file indicate whether or not contents may
				// be viewed?
				boolean mayViewContentsSet = indexConfig.has("mayViewContent");
				if (mayViewContentsSet) {
					// Yes; store the setting.
					boolean mayViewContent = indexConfig
							.getBoolean("mayViewContent");
					indexParam.put(indexName, new IndexParam(dir, pid,
							mayViewContent));
				} else {
					// No; record that we don't know (i.e. use the index
					// metadata setting).
					indexParam.put(indexName, new IndexParam(dir, pid));
				}

				indicesFound = true;
			}
		}

		// Collections
		collectionsDirs = new ArrayList<>();
		if (properties.has("indexCollections")) {
			JSONArray indexCollectionsList = properties
					.getJSONArray("indexCollections");
			for (int i = 0; i < indexCollectionsList.length(); i++) {
				String strIndexCollection = indexCollectionsList.getString(i);
				File indexCollection = new File(strIndexCollection);
				if (indexCollection.canRead()) {
					indicesFound = true; // even if it contains none now, it
											// could in the future
					collectionsDirs.add(indexCollection);
				} else {
					logger.warn("Configured collection not found or not readable: "
							+ indexCollection);
				}
			}
		}

		// User collections dir
		if (properties.has("userCollectionsDir")) {
			userCollectionsDir = new File(
					properties.getString("userCollectionsDir"));
			if (!userCollectionsDir.canRead()) {
				logger.error("Configured user collections dir not found or not readable: "
						+ userCollectionsDir);
				userCollectionsDir = null;
			} else {
				indicesFound = true; // even if it contains none now, it
				                     // could in the future
			}
		}

		if (!indicesFound) {
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
	 *
	 * @return the user's collection dir, or null if none
	 */
	private File getUserCollectionDir(String userId) {
		if (userCollectionsDir == null)
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
	 * Find an index given its name.
	 *
	 * Looks at explicitly configured indices as well as collections.
	 *
	 * If a user is logged in, only looks in the user's private index
	 * collection.
	 *
	 * @param indexName
	 *            the index name
	 * @return the index dir and mayViewContents setting
	 */
	private synchronized IndexParam getIndexParam(String indexName) {
		// Already in the cache?
		if (indexParam.containsKey(indexName)) {
			IndexParam p = indexParam.get(indexName);

			// Check if it's still there.
			if (p.getDir().canRead())
				return p;

			// Directory isn't accessible any more; remove from cache
			indexParam.remove(indexName);
			cache.clearCacheForIndex(indexName);
		}

		// Is it a private index?
		if (indexName.contains(":")) {
			// Yes; look in user's private index collection.
			String[] parts = indexName.split(":");
			File dir = getUserCollectionDir(parts[0]);
			return findIndexInCollection(parts[1], dir, true, parts[0] + ":");
		}

		// Find it in a collection
		for (File collection : collectionsDirs) {
			IndexParam p = findIndexInCollection(indexName, collection, true, "");
			if (p != null)
				return p;
		}

		return null;
	}

	public File getIndexDir(String indexName) {
		return getIndexParam(indexName).getDir();
	}

	/**
	 * Search a collection for an index name.
	 *
	 * Adds index parameters to the cache if found.
	 *
	 * @param name
	 *            name of the index, without user prefix (if any)
	 * @param collection
	 *            the collection dir
	 * @param addToCache
	 *            if true, add parameters to the cache if found
	 * @param userIdPrefix what to prefix the name with when putting it in the cache
	 * @param parts
	 * @return the index parameters if found.
	 */
	private synchronized IndexParam findIndexInCollection(String name, File collection,
			boolean addToCache, String userIdPrefix) {
		// Look for the index in this collection dir
		File dir = new File(collection, name);
		if (dir.canRead() && Searcher.isIndex(dir)) {
			// Found it. Add to the cache and return
			IndexParam p = new IndexParam(dir);
			if (addToCache)
				indexParam.put(userIdPrefix + name, p);
			return p;
		}
		return null;
	}

	public synchronized void closeSearcher(String indexName) throws BlsException {
		if (!BlsUtils.isValidIndexName(indexName))
			throw new IllegalIndexName(indexName);
		if (searchers.containsKey(indexName)) {
			searchers.get(indexName).close();
			searchers.remove(indexName);
			indexStatus.remove(indexName);
			cache.clearCacheForIndex(indexName);
		}
	}

	/**
	 * Get the Searcher object for the specified index.
	 *
	 * @param indexName
	 *            the index we want to search
	 * @return the Searcher object for that index
	 * @throws BlsException
	 *             if not found or open error
	 */
	@SuppressWarnings("deprecation")  // for call to _setPidField() and _setContentViewable()
	public synchronized Searcher getSearcher(String indexName)
			throws BlsException {
		if (!BlsUtils.isValidIndexName(indexName))
			throw new IllegalIndexName(indexName);

		if (searchers.containsKey(indexName)) {
			Searcher searcher = searchers.get(indexName);
			if (searcher.getIndexDirectory().canRead())
				return searcher;
			// Index was (re)moved; remove Searcher from cache.
			searchers.remove(indexName);
			indexStatus.remove(indexName);
			cache.clearCacheForIndex(indexName);
			// Maybe we can find an index with this name elsewhere?
		}
		IndexParam par = getIndexParam(indexName);
		if (par == null) {
			throw new IndexNotFound(indexName);
		}
		File indexDir = par.getDir();
		Searcher searcher;
		try {
			logger.debug("Opening index '" + indexName + "', dir = " + indexDir);
			searcher = Searcher.open(indexDir);
			searcher.setDefaultSearchSensitive(searchMan.config().isDefaultCaseSensitive(), searchMan.config().isDefaultDiacriticsSensitive());
		} catch (Exception e) {
			throw new InternalServerError("Could not open index '" + indexName
					+ "'", 27, e);
		}
		searchers.put(indexName, searcher);

		// Figure out the pid from the index metadata and/or BLS config.
		String indexPid = searcher.getIndexStructure().pidField();
		if (indexPid == null)
			indexPid = "";
		String configPid = par.getPidField();
		if (indexPid.length() > 0 && !configPid.equals(indexPid)) {
			if (configPid.length() > 0) {
				logger.error("Different pid field configured in blacklab-server.json than in index metadata! ("
						+ configPid + "/" + indexPid + "); using the latter");
			}
			// Update index parameters with the pid field found in the metadata
			par.setPidField(indexPid);
		} else {
			// No pid configured in index, only in blacklab-server.json. We want
			// to get rid
			// of this (prints an error on startup), but it should still work
			// for now. Inject
			// the setting into the searcher.
			if (configPid.length() > 0)
				searcher.getIndexStructure()._setPidField(configPid);
		}
		if (indexPid.length() == 0 && configPid.length() == 0) {
			logger.warn("No pid given for index '" + indexName
					+ "'; using Lucene doc ids.");
		}

		// Look for the contentViewable setting in the index metadata
		boolean contentViewable = searcher.getIndexStructure()
				.contentViewable();
		boolean blsConfigContentViewable = par.mayViewContents();
		if (par.mayViewContentsSpecified()
				&& contentViewable != blsConfigContentViewable) {
			logger.error("Index metadata and blacklab-server.json configuration disagree on content view settings! Disallowing free content viewing.");
			par.setMayViewContent(false);
			searcher.getIndexStructure()._setContentViewable(false);
		}

		return searcher;
	}

	/**
	 * Does the specified index exist?
	 *
	 * @param indexName
	 *            the index we want to check for
	 * @return true iff the index exists
	 * @throws BlsException
	 */
	public boolean indexExists(String indexName)
			throws BlsException {
		if (!BlsUtils.isValidIndexName(indexName))
			throw new IllegalIndexName(indexName);
		IndexParam par = getIndexParam(indexName);
		if (par == null) {
			return false;
		}
		return Searcher.isIndex(par.getDir());
	}

	/**
	 * Create an empty user index.
	 *
	 * Indices may only be created by a logged-in user in his own private area.
	 * The index name is strictly validated, disallowing any weird input.
	 *
	 * @param indexName
	 *            the index name, including user prefix
	 * @param displayName
	 * @param documentFormat the document format identifier (e.g. tei, folia, ..)
	 * @throws BlsException
	 *             if we're not allowed to create the index for whatever reason
	 * @throws IOException
	 *             if creation failed unexpectedly
	 */
	public void createIndex(String indexName, String displayName, String documentFormat) throws BlsException,
			IOException {
		if (!indexName.contains(":"))
			throw new NotAuthorized("Can only create private indices.");
		if (!BlsUtils.isValidIndexName(indexName))
			throw new IllegalIndexName(indexName);
		if (indexExists(indexName))
			throw new BadRequest("INDEX_ALREADY_EXISTS",
					"Could not create index. Index already exists.");
		String[] parts = indexName.split(":");
		String userId = parts[0];
		String indexNameWithoutUsePrefix = parts[1];
		if (!canCreateIndex(userId))
			throw new BadRequest("CANNOT_CREATE_INDEX ",
					"Could not create index. You already have the maximum of "
							+ IndexManager.MAX_USER_INDICES + " indices.");

		File userDir = getUserCollectionDir(userId);
		if (userDir == null || !userDir.canWrite())
			throw new InternalServerError("Could not create index. Cannot write in user dir: " + userDir, 16);

		File indexDir = new File(userDir, indexNameWithoutUsePrefix);
		boolean contentViewable = true; // user may view his own private corpus documents
		Searcher searcher = Searcher.createIndex(indexDir, displayName, documentFormat, contentViewable);
		searcher.close();
	}

	public boolean canCreateIndex(String userId) {
		return getAvailablePrivateIndices(userId).size() < IndexManager.MAX_USER_INDICES;
	}

	/**
	 * Delete a user index.
	 *
	 * Only user indices are deletable. The owner must be logged in. The index
	 * name is strictly validated, disallowing any weird input. Many other
	 * checks are done to root out all kinds of special cases.
	 *
	 * @param indexName
	 *            the index name
	 *
	 * @throws BlsException
	 *             if we're not allowed to delete the index
	 */
	public void deleteUserIndex(String indexName)
			throws BlsException {
		if (!indexName.contains(":"))
			throw new NotAuthorized("Can only delete private indices.");
		if (!BlsUtils.isValidIndexName(indexName))
			throw new IllegalIndexName(indexName);
		if (!indexExists(indexName))
			throw new IndexNotFound(indexName);
		String[] parts = indexName.split(":");
		String userId = parts[0];
		String indexNameNoUserPrefix = parts[1];
		File userDir = getUserCollectionDir(userId);
		File indexDir = new File(userDir, indexNameNoUserPrefix);
		if (!indexDir.isDirectory())
			throw new InternalServerError("Could not delete index. Not an index.", 17);
		if (!userDir.canWrite() || !indexDir.canWrite())
			throw new InternalServerError("Could not delete index. Check file permissions.", 18);
		if (!indexDir.getParentFile().equals(userDir)) { // Yes, we're paranoid..
			throw new InternalServerError("Could not delete index. Not found in user dir.", 19);
		}
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

		// Remove stuff from the cache, close Searcher
		cache.clearCacheForIndex(indexName);
		Searcher searcher = getSearcher(indexName);
		if (searcher != null) {
			searchers.remove(indexName);
			indexStatus.remove(indexName);
			searcher.close();
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
		BlsUtils.delTree(indexDir);
	}

	/**
	 * Return the list of private indices available for searching.
	 *
	 * @param userId the user
	 * @return the list of index names
	 */
	public Collection<String> getAvailablePrivateIndices(String userId) {

		File userDir = getUserCollectionDir(userId);
		Set<String> indices = new HashSet<>();
		if (userDir != null) {
			for (File f : userDir.listFiles(BlsUtils.readableDirFilter)) {
				indices.add(userId + ":" + f.getName());
			}
		}
		return indices;
	}

	/**
	 * Return the list of public indices available for searching.
	 *
	 * @return the list of index names
	 */
	public synchronized Collection<String> getAvailablePublicIndices() {
		Set<String> indices = new HashSet<>();

		// Scan collections for any new indices
		for (File dir : collectionsDirs) {
			for (File f : dir.listFiles(BlsUtils.readableDirFilter)) {
				if (!indexParam.containsKey(f.getName()) && Searcher.isIndex(f)) {
					// New one; add it
					indexParam.put(f.getName(), new IndexParam(f));
				}
			}
		}

		// Gather list of public indices, and
		// remove indices that are no longer available
		List<String> remove = new ArrayList<>();
		for (Map.Entry<String, IndexParam> e : indexParam.entrySet()) {
			if (!e.getValue().getDir().canRead()) {
				remove.add(e.getKey());
			} else {
				if (!e.getKey().contains(":"))
					indices.add(e.getKey());
			}
		}
		for (String name : remove) {
			indexParam.remove(name);
		}

		return indices;
	}

	/**
	 * Check the current status of an index
	 *
	 * @param indexName
	 *            the index
	 * @return the current status
	 * @throws BlsException
	 */
	public String getIndexStatus(String indexName) throws BlsException {
		synchronized (indexStatus) {
			String status = indexStatus.get(indexName);
			if (status == null) {
				if (getSearcher(indexName).isEmpty()) {
					status = "empty";
				} else {
					status = "available";
				}
			}
			return status;
		}
	}

	/**
	 * Check if the index status is (still) the specified status, and if so,
	 * update the status to the new one.
	 *
	 * To check if setting was succesful, see if the returned value equals the
	 * requested status.
	 *
	 * @param indexName
	 *            the index to set the status for
	 * @param checkOldStatus
	 *            only set the new status if this pattern matches the current status;
	 *            if null, ignore it.
	 * @param status
	 *            the new status
	 * @return the resulting status of the index
	 * @throws BlsException
	 */
	public String setIndexStatus(String indexName, String checkOldStatus,
			String status) throws BlsException {
		synchronized (indexStatus) {
			String oldStatus = getIndexStatus(indexName);
			if (checkOldStatus != null && !oldStatus.matches(checkOldStatus))
				return oldStatus;
			indexStatus.put(indexName, status);
			return status;
		}
	}

	public List<String> getAllAvailableIndices(String userId) {
		List<String> indices = new ArrayList<>();
		if (userId != null && userId.length() > 0)
			indices.addAll(getAvailablePrivateIndices(userId));
		indices.addAll(getAvailablePublicIndices());
		Collections.sort(indices, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				// Sort public before private
				boolean o1priv = o1.contains(":");
				boolean o2priv = o2.contains(":");
				if (o1priv != o2priv)
					return o1priv ? 1 : -1;

				// Sort rest case-insensitively
	            return o1.toLowerCase().compareTo(o2.toLowerCase());
			}
	    });
		return indices;
	}


}