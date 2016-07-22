package nl.inl.blacklab.server.search;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.queryParser.corpusql.ParseException;
import nl.inl.blacklab.queryParser.corpusql.TokenMgrError;
import nl.inl.blacklab.search.CompleteQuery;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.ServletUtil;
import nl.inl.blacklab.server.dataobject.DataFormat;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.dataobject.DataObjectString;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.exceptions.IllegalIndexName;
import nl.inl.blacklab.server.exceptions.IndexNotFound;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.exceptions.TooManyRequests;
import nl.inl.util.FileUtil;
import nl.inl.util.FileUtil.FileTask;
import nl.inl.util.MemoryUtil;
import nl.inl.util.ThreadPriority;
import nl.inl.util.json.JSONArray;
import nl.inl.util.json.JSONException;
import nl.inl.util.json.JSONObject;

public class SearchManager {
	private static final Logger logger = Logger.getLogger(SearchManager.class);

	private static final int MAX_USER_INDICES = 10;

	public static final String ILLEGAL_NAME_ERROR = "is not a valid index name (only letters, digits, underscores and dashes allowed, and must start with a letter)";

	/**
	 * If enabled, this makes sure the SearchCache will follow the behaviour
	 * rules set in blacklab-server.json to lowprio/pause searches in certain
	 * situations under certain loads.
	 * (EXPERIMENTAL)
	 */
	static final boolean ENABLE_THREAD_PRIORITY = true;

	/**
	 * A file filter that returns readable directories only; used for scanning
	 * collections dirs
	 */
	private static FileFilter readableDirFilter = new FileFilter() {
		@Override
		public boolean accept(File f) {
			return f.isDirectory() && f.canRead();
		}
	};

	/**
	 * Are we allowed to query the list of all document?
	 * (slow for large corpora)
	 * TODO: make configurable
	 */
	private static boolean allDocsQueryAllowed = false;

	/**
	 * When the SearchManager was created. Used in logging to show ms since
	 * server start instead of all-time.
	 */
	long createdAt = System.currentTimeMillis();

	/**
	 * How long the server should wait for a quick answer when starting a
	 * nonblocking request. If the answer is found within this time, the client
	 * needs only one request even in nonblocking mode.
	 */
	private int waitTimeInNonblockingModeMs;

	/**
	 * The minimum time to advise a client to wait before checking the status of
	 * a search again.
	 */
	private int checkAgainAdviceMinimumMs;

	/**
	 * What number to divide the search time so far by to get the check again
	 * advice. E.g. if this is set to 5 (the default), if a search has been
	 * running for 10 seconds, clients are advised to wait 2 seconds before
	 * checking the status again.
	 */
	private int checkAgainAdviceDivider;

	/** Maximum context size allowed */
	private int maxContextSize;

	/** Maximum snippet size allowed */
	private int maxSnippetSize;

	// TODO: move to SearchParamaters?
	/** Default values for request parameters */
	final static private Map<String, String> defaultParameterValues = new HashMap<>();

	/** Default number of hits/results per page [20] */
	private int defaultPageSize;

	/** Maximum value allowed for number parameter */
	private int maxPageSize;

	/** Our current set of indices (with dir and mayViewContent setting) */
	private Map<String, IndexParam> indexParam;

	/**
	 * The status of each index, i.e. "available" or "indexing". If no status is
	 * stored here, the status is "available".
	 */
	Map<String, String> indexStatus;

	/** The Searcher objects, one for each of the indices we can search. */
	private Map<String, Searcher> searchers = new HashMap<>();

//	/** The IndexJob objects, one for each of the indices we're adding to. */
//	private Map<String, IndexJob> indexJobs = new HashMap<String, IndexJob>();

	/** Configured index collections directories */
	private List<File> collectionsDirs;

	/**
	 * Logged-in users will have their own private collections dir. This is the
	 * parent of that dir.
	 */
	private File userCollectionsDir;

	/** All running searches as well as recently run searches */
	private SearchCache cache;

	/** Keeps track of running jobs per user, so we can limit this. */
	private Map<String, Set<Job>> runningJobsPerUser = new HashMap<>();

	/** Default pattern language to use. [corpusql] */
	private String defaultPatternLanguage;

	/** Default case-sensitivity to use. [insensitive] */
	private boolean defaultCaseSensitive;

	/** Default diacritics-sensitivity to use. [insensitive] */
	private boolean defaultDiacriticsSensitive;

	/** Default filter language to use. [luceneql] */
	private String defaultFilterLanguage;

	/** Should requests be blocking by default? [yes] */
	private boolean defaultBlockingMode;

	/** Default number of words around hit. [5] */
	private int defaultContextSize;

	/** Minimum amount of free memory (MB) to start a new search. [50] */
	private int minFreeMemForSearchMegs;

	/**
	 * Maximum number of simultaneously running jobs started by the same user.
	 * [20] Please note that a search may start 2-4 jobs, so don't set this too
	 * low. This is just meant to prevent over-eager scripts and other abuse.
	 * Regular users should never hit this limit.
	 */
	private long maxRunningJobsPerUser;

	/** IP addresses for which debug mode will be turned on. */
	private Set<String> debugModeIps = new HashSet<>();

	/** The default output type, JSON or XML. */
	private DataFormat defaultOutputType;

	/**
	 * Which IPs are allowed to override the userId using a parameter.
	 */
	private Set<String> overrideUserIdIps;

	/**
	 * How long the client may used a cached version of the results we give
	 * them. This is used to write HTTP cache headers. A value of an hour or so
	 * seems reasonable.
	 */
	private int clientCacheTimeSec;

	/** Maximum allowed value for maxretrieve parameter (-1 = no limit). */
	private int maxHitsToRetrieveAllowed;

	/** Maximum allowed value for maxcount parameter (-1 = no limit). */
	private int maxHitsToCountAllowed;

	/** The authentication system, giving information about the currently logged-in user
        (or at least a session id) */
	private Object authSystem;

	/** The method to invoke for determining the current user. */
	private Method authMethodDetermineCurrentUser;

	/**
	 * A thread that calls performLoadSpecificBehaviour(null) regularly,
	 * to ensure load management continues even if no new requests are coming in.
	 */
	private Thread loadManagerThread;

	@SuppressWarnings("deprecation")
	public SearchManager(JSONObject properties) throws ConfigurationException {
		logger.debug("SearchManager created");

		try {
			// this.properties = properties;
			if (properties.has("debugModeIps")) {
				JSONArray jsonDebugModeIps = properties
						.getJSONArray("debugModeIps");
				for (int i = 0; i < jsonDebugModeIps.length(); i++) {
					debugModeIps.add(jsonDebugModeIps.getString(i));
				}
			}

			// Request properties
			if (properties.has("requests")) {
				JSONObject reqProp = properties.getJSONObject("requests");
				defaultOutputType = DataFormat.XML; // XML if nothing specified
													// (because
													// of browser's default Accept
													// header)
				if (reqProp.has("defaultOutputType"))
					defaultOutputType = ServletUtil.getOutputTypeFromString(
							reqProp.getString("defaultOutputType"), DataFormat.XML);
				defaultPageSize = JsonUtil.getIntProp(reqProp, "defaultPageSize",
						20);
				maxPageSize = JsonUtil.getIntProp(reqProp, "maxPageSize", 1000);
				defaultPatternLanguage = JsonUtil.getProperty(reqProp,
						"defaultPatternLanguage", "corpusql");
				String defaultSearchSensitivity = JsonUtil.getProperty(reqProp,
						"defaultSearchSensitivity", "insensitive");
				switch(defaultSearchSensitivity) {
				case "sensitive":
					defaultCaseSensitive = defaultDiacriticsSensitive = true;
					break;
				case "case":
					defaultCaseSensitive = true;
					defaultDiacriticsSensitive = false;
					break;
				case "diacritics":
					defaultDiacriticsSensitive = true;
					defaultCaseSensitive = false;
					break;
				default:
					defaultCaseSensitive = defaultDiacriticsSensitive = false;
					break;
				}
				defaultFilterLanguage = JsonUtil.getProperty(reqProp,
						"defaultFilterLanguage", "luceneql");
				defaultBlockingMode = JsonUtil.getBooleanProp(reqProp,
						"defaultBlockingMode", true);
				defaultContextSize = JsonUtil.getIntProp(reqProp,
						"defaultContextSize", 5);
				maxContextSize = JsonUtil.getIntProp(reqProp, "maxContextSize", 20);
				maxSnippetSize = JsonUtil
						.getIntProp(reqProp, "maxSnippetSize", 100);
				Hits.setDefaultMaxHitsToRetrieve(JsonUtil.getIntProp(reqProp,
						"defaultMaxHitsToRetrieve",
						Hits.getDefaultMaxHitsToRetrieve()));
				Hits.setDefaultMaxHitsToCount(JsonUtil.getIntProp(reqProp,
						"defaultMaxHitsToCount", Hits.getDefaultMaxHitsToCount()));
				maxHitsToRetrieveAllowed = JsonUtil.getIntProp(reqProp,
						"maxHitsToRetrieveAllowed", 10000000);
				maxHitsToCountAllowed = JsonUtil.getIntProp(reqProp,
						"maxHitsToCountAllowed", -1);
				JSONArray jsonOverrideUserIdIps = reqProp
						.getJSONArray("overrideUserIdIps");
				overrideUserIdIps = new HashSet<>();
				for (int i = 0; i < jsonOverrideUserIdIps.length(); i++) {
					overrideUserIdIps.add(jsonOverrideUserIdIps.getString(i));
				}
			} else {
				defaultOutputType = DataFormat.XML;
				defaultPageSize = 20;
				maxPageSize = 1000;
				defaultPatternLanguage = "corpusql";
				defaultCaseSensitive = defaultDiacriticsSensitive = false;
				defaultFilterLanguage = "luceneql";
				defaultBlockingMode = true;
				defaultContextSize = 5;
				maxContextSize = 20;
				maxSnippetSize = 100;
				maxHitsToRetrieveAllowed = 10000000;
				maxHitsToCountAllowed = -1;
				overrideUserIdIps = new HashSet<>();
			}

			// Performance properties
			if (properties.has("performance")) {
				JSONObject perfProp = properties.getJSONObject("performance");
				minFreeMemForSearchMegs = JsonUtil.getIntProp(perfProp, "minFreeMemForSearchMegs", 50);
				maxRunningJobsPerUser = JsonUtil.getIntProp(perfProp, "maxRunningJobsPerUser", 20);
				checkAgainAdviceMinimumMs = JsonUtil.getIntProp(perfProp, "checkAgainAdviceMinimumMs", 200);
				checkAgainAdviceDivider = JsonUtil.getIntProp(perfProp, "checkAgainAdviceDivider", 5);
				waitTimeInNonblockingModeMs = JsonUtil.getIntProp(perfProp, "waitTimeInNonblockingModeMs", 100);
				clientCacheTimeSec = JsonUtil.getIntProp(perfProp, "clientCacheTimeSec", 3600);

				// Cache properties
				JSONObject cacheProp = perfProp.getJSONObject("cache");

				// Start with empty cache
				cache = new SearchCache(cacheProp);

				JSONObject jsonServerLoad = null;
				if (perfProp.has("serverLoad")) {
					// Load manager stuff (experimental)

					// Make sure long operations yield their thread occasionally,
					// and automatically abort really long operations.
					ThreadPriority.setEnabled(ENABLE_THREAD_PRIORITY);

					jsonServerLoad = perfProp.getJSONObject("serverLoad");
				}
				cache.setServerLoadOptions(jsonServerLoad);
			} else {
				// Set default values
				minFreeMemForSearchMegs = 50;
				maxRunningJobsPerUser = 20;
				checkAgainAdviceMinimumMs = 200;
				checkAgainAdviceDivider = 5;
				waitTimeInNonblockingModeMs = 100;
				clientCacheTimeSec = 3600;
				cache = new SearchCache();
			}

			// Find the indices
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

			// Init auth system
			String authClass = "";
			Map<String, Object> authParam;
			if (properties.has("authSystem")) {
				JSONObject propAuth = properties.getJSONObject("authSystem");
				authParam = JsonUtil.mapFromJsonObject(propAuth);
				if (authParam.containsKey("class")) {
					authClass = authParam.get("class").toString();
				}
			} else {
				authParam = new HashMap<>();
			}
			if (authClass.length() > 0) {
				try {
					if (!authClass.contains(".")) {
						// Allows us to abbreviate the built-in auth classes
						authClass = "nl.inl.blacklab.server.auth." + authClass;
					}
					Class<?> cl = Class.forName(authClass);
					authSystem = cl.getConstructor(Map.class).newInstance(authParam);
					authMethodDetermineCurrentUser = cl.getMethod("determineCurrentUser", HttpServlet.class, HttpServletRequest.class);
				} catch (Exception e) {
					throw new RuntimeException("Error instantiating auth system: " + authClass, e);
				}
				logger.info("Auth system initialized: " + authClass);
			} else {
				logger.info("No auth system configured");
			}
		} catch (JSONException e) {
			e.printStackTrace();
			throw new ConfigurationException("Invalid JSON in blacklab-server.json; please validate: " + e.getMessage());
		}

		// Set up the parameter default values
		//defaultParameterValues = new HashMap<>();
		defaultParameterValues.put("filterlang", defaultFilterLanguage);
		defaultParameterValues.put("pattlang", defaultPatternLanguage);
		defaultParameterValues.put("sort", "");
		defaultParameterValues.put("group", "");
		defaultParameterValues.put("viewgroup", "");
		defaultParameterValues.put("first", "0");
		defaultParameterValues.put("hitstart", "0");
		defaultParameterValues.put("hitend", "1");
		defaultParameterValues.put("number", "" + defaultPageSize);
		defaultParameterValues.put("block", defaultBlockingMode ? "yes" : "no");
		defaultParameterValues.put("waitfortotal", "no");
		defaultParameterValues.put("includetokencount", "no");
		defaultParameterValues.put("wordsaroundhit", "" + defaultContextSize);
		defaultParameterValues.put("usecontent", "fi");
		defaultParameterValues.put("wordstart", "-1");
		defaultParameterValues.put("wordend", "-1");
		defaultParameterValues.put("calc", "");
		defaultParameterValues.put("maxretrieve",
				"" + Hits.getDefaultMaxHitsToRetrieve());
		defaultParameterValues.put("maxcount",
				"" + Hits.getDefaultMaxHitsToCount());
		defaultParameterValues.put("sensitive", defaultCaseSensitive && defaultDiacriticsSensitive ? "yes" : "no");
		defaultParameterValues.put("property", "word");

		loadManagerThread = new LoadManagerThread(this);
		loadManagerThread.start();

	}

	/**
	 * Clean up resources.
	 *
	 * In particular, stops the load manager thread and
	 * cancels any running searches.
	 */
	public synchronized void cleanup() {
		// Stop the load manager thread
		loadManagerThread.interrupt();
		loadManagerThread = null;

		// Stop any running searches
		cache.clearCache(true);
	}

	public synchronized void performLoadManagement() {
		cache.performLoadManagement(null);
	}

	public User determineCurrentUser(HttpServlet servlet, HttpServletRequest request) {
		// If no auth system is configured, all users are anonymous
		if (authSystem == null) {
			User user = User.anonymous(request.getSession().getId());
			//logger.debug("No auth system, user = " + user);
			return user;
		}

		// Let auth system determine the current user.
		try {
			User user = (User)authMethodDetermineCurrentUser.invoke(authSystem, servlet, request);
			//logger.debug("User = " + user);
			return user;
		} catch (Exception e) {
			throw new RuntimeException("Error determining current user", e);
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

	final static public List<String> getSearchParameterNames() {
		return SearchParameters.NAMES;
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
		//logger.debug("@PERF getIndexParam");
		try {

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

		} finally {
			//logger.debug("@PERF getIndexParam EXIT");
		}
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

	/**
	 * Check the index name part (not the user id part, if any)
	 * of the specified index name.
	 *
	 * @param indexName the index name, possibly including user id prefix
	 * @return whether or not the index name part is valid
	 */
	public static boolean isValidIndexName(String indexName) {
		if (indexName.contains(":")) {
			String[] parts = indexName.split(":");
			indexName = parts[1];
		}
		return indexName.matches("[a-zA-Z][a-zA-Z0-9_\\-]*");
	}

	public synchronized void closeSearcher(String indexName) throws BlsException {
		if (!isValidIndexName(indexName))
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
	@SuppressWarnings("deprecation")
	// for call to _setPidField() and _setContentViewable()
	public synchronized Searcher getSearcher(String indexName)
			throws BlsException {
		//logger.debug("@PERF getSearcher");
		try {

			if (!isValidIndexName(indexName))
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
				searcher.setDefaultSearchSensitive(defaultCaseSensitive, defaultDiacriticsSensitive);
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

		} finally {
			//logger.debug("@PERF getSearcher EXIT");
		}
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
		if (!isValidIndexName(indexName))
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
		if (!isValidIndexName(indexName))
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
							+ MAX_USER_INDICES + " indices.");

		File userDir = getUserCollectionDir(userId);
		if (userDir == null || !userDir.canWrite())
			throw new InternalServerError("Could not create index. Cannot write in user dir: " + userDir, 16);

		File indexDir = new File(userDir, indexNameWithoutUsePrefix);
		boolean contentViewable = true; // user may view his own private corpus documents
		Searcher searcher = Searcher.createIndex(indexDir, displayName, documentFormat, contentViewable);
		searcher.close();
	}

	public boolean canCreateIndex(String userId) {
		return getAvailablePrivateIndices(userId).size() < MAX_USER_INDICES;
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
		if (!isValidIndexName(indexName))
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
			if (isSymlink(indexDir)) {
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
		delTree(indexDir);
	}

	// Copied from Apache Commons
	// (as allowed under the Apache License 2.0)
	public static boolean isSymlink(File file) throws IOException {
		if (file == null)
			throw new NullPointerException("File must not be null");
		File canon;
		if (file.getParent() == null) {
			canon = file;
		} else {
			File canonDir = file.getParentFile().getCanonicalFile();
			canon = new File(canonDir, file.getName());
		}
		return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
	}

	/**
	 * Delete an entire tree with files, subdirectories, etc.
	 *
	 * CAREFUL, DANGEROUS!
	 *
	 * @param root
	 *            the directory tree to delete
	 */
	private static void delTree(File root) {
		if (!root.isDirectory())
			throw new RuntimeException("Not a directory: " + root);
		for (File f : root.listFiles()) {
			if (f.isDirectory())
				delTree(f);
			else
				f.delete();
		}
		root.delete();
	}

	/**
	 * Get the Lucene Document id given the pid
	 *
	 * @param searcher
	 *            our index
	 * @param pid
	 *            the pid string (or Lucene doc id if we don't use a pid)
	 * @return the document id, or -1 if it doesn't exist
	 */
	public static int getLuceneDocIdFromPid(Searcher searcher, String pid)
			{
		//logger.debug("@PERF getLuceneDocIdFromPid");
		try {

		String pidField = searcher.getIndexStructure().pidField(); // getIndexParam(indexName,
																	// user).getPidField();
		// Searcher searcher = getSearcher(indexName, user);
		if (pidField == null || pidField.length() == 0) {
			int luceneDocId;
			try {
				luceneDocId = Integer.parseInt(pid);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(
						"Pid must be a Lucene doc id, but it's not a number: "
								+ pid);
			}
			return luceneDocId;
		}
		boolean lowerCase = false; // HACK in case pid field is incorrectly
									// lowercased
		DocResults docResults;
		while (true) {
			String p = lowerCase ? pid.toLowerCase() : pid;
			TermQuery documentFilterQuery = new TermQuery(new Term(pidField, p));
			docResults = searcher.queryDocuments(documentFilterQuery);
			if (docResults.size() > 1) {
				// Should probably throw a fatal exception, but sometimes
				// documents
				// accidentally occur twice in a dataset...
				// TODO: make configurable whether or not a fatal exception is
				// thrown
				logger.error("Pid must uniquely identify a document, but it has "
						+ docResults.size() + " hits: " + pid);
			}
			if (docResults.size() == 0) {
				if (lowerCase)
					return -1; // tried with and without lowercasing; doesn't
								// exist
				lowerCase = true; // try lowercase now
			} else {
				// size == 1, found!
				break;
			}
		}
		return docResults.get(0).getDocId();

		} finally {
			//logger.debug("@PERF getLuceneDocIdFromPid EXIT");
		}
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
			for (File f : userDir.listFiles(readableDirFilter)) {
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
			for (File f : dir.listFiles(readableDirFilter)) {
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

	public JobWithHits searchHits(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "sort", "docpid",
				"maxretrieve", "maxcount", "sample", "samplenum", "sampleseed");
		String sort = parBasic.get("sort");
		if (sort != null && sort.length() > 0) {
			// Sorted hits
			parBasic.put("jobclass", "JobHitsSorted");
			return (JobHitsSorted) search(user, parBasic);
		}

		// No sort
		parBasic.remove("sort"); // unsorted must not include sort parameter, or
									// it's cached wrong
		parBasic.put("jobclass", "JobHits");
		return (JobHits) search(user, parBasic);
	}

	public JobWithDocs searchDocs(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "sort", "usecontent",
				"maxretrieve", "maxcount");
		String sort = parBasic.get("sort");
		if (sort != null && sort.length() > 0) {
			// Sorted hits
			parBasic.put("jobclass", "JobDocsSorted");
			return (JobDocsSorted) search(user, parBasic);
		}

		// No sort
		parBasic.remove("sort"); // unsorted must not include sort parameter, or
									// it's cached wrong
		parBasic.put("jobclass", "JobDocs");
		return (JobDocs) search(user, parBasic);
	}

	public JobHitsWindow searchHitsWindow(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "sort", "first", "number",
				"wordsaroundhit", "usecontent", "maxretrieve", "maxcount",
				"sample", "samplenum", "sampleseed");
		parBasic.put("jobclass", "JobHitsWindow");
		return (JobHitsWindow) search(user, parBasic);
	}

	public JobDocsWindow searchDocsWindow(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "sort", "first", "number",
				"wordsaroundhit", "usecontent", "maxretrieve", "maxcount");
		parBasic.put("jobclass", "JobDocsWindow");
		return (JobDocsWindow) search(user, parBasic);
	}

	public JobHitsTotal searchHitsTotal(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "maxretrieve", "maxcount",
				"sample", "samplenum", "sampleseed");
		parBasic.put("jobclass", "JobHitsTotal");
		return (JobHitsTotal) search(user, parBasic);
	}

	public JobDocsTotal searchDocsTotal(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "maxretrieve", "maxcount");
		parBasic.put("jobclass", "JobDocsTotal");
		return (JobDocsTotal) search(user, parBasic);
	}

	public JobHitsGrouped searchHitsGrouped(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "group", "sort",
				"maxretrieve", "maxcount", "sample", "samplenum", "sampleseed");
		parBasic.put("jobclass", "JobHitsGrouped");
		return (JobHitsGrouped) search(user, parBasic);
	}

	public JobDocsGrouped searchDocsGrouped(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("indexname", "patt",
				"pattlang", "filter", "filterlang", "group", "sort",
				"maxretrieve", "maxcount");
		parBasic.put("jobclass", "JobDocsGrouped");
		return (JobDocsGrouped) search(user, parBasic);
	}

	public JobFacets searchFacets(User user, SearchParameters par)
			throws BlsException {
		SearchParameters parBasic = par.copyWithOnly("facets", "indexname",
				"patt", "pattlang", "filter", "filterlang");
		parBasic.put("jobclass", "JobFacets");
		return (JobFacets) search(user, parBasic);
	}

	/**
	 * Start a new search or return an existing Search object corresponding to
	 * these search parameters.
	 *
	 * @param user
	 *            user creating the job
	 * @param searchParameters
	 *            the search parameters
	 * @param blockUntilFinished
	 *            if true, wait until the search finishes; otherwise, return
	 *            immediately
	 * @return a Search object corresponding to these parameters
	 * @throws BlsException
	 *             if the query couldn't be executed
	 */
	private Job search(User user, SearchParameters searchParameters)
			throws BlsException {
		//logger.debug("@PERF search");
		try {
			// Search the cache / running jobs for this search, create new if not
			// found.
			boolean performSearch = false;
			Job job;
			synchronized (this) {
				job = cache.get(searchParameters);
				if (job == null) {
					// Not found in cache

					// Do we have enough memory to start a new search?
					long freeMegs = MemoryUtil.getFree() / 1000000;
					if (freeMegs < minFreeMemForSearchMegs) {
						cache.removeOldSearches(); // try to free up space for next
													// search
						logger.warn("Can't start new search, not enough memory ("
								+ freeMegs + "M < " + minFreeMemForSearchMegs
								+ "M)");
						logger.warn("(NOTE: make sure Tomcat's max heap mem is set to an appropriate value!)");
						throw new ServiceUnavailable("The server seems to be under heavy load right now. Please try again later.");
					}
					// logger.debug("Enough free memory: " + freeMegs + "M");

					// Is this user allowed to start another search?
					int numRunningJobs = 0;
					String uniqueId = user.uniqueId();
					Set<Job> runningJobs = runningJobsPerUser.get(uniqueId);
					Set<Job> newRunningJobs = new HashSet<>();
					if (runningJobs != null) {
						for (Job runningJob : runningJobs) {
							if (!runningJob.finished()) {
								numRunningJobs++;
								newRunningJobs.add(runningJob);
							}
						}
					}
					if (numRunningJobs >= maxRunningJobsPerUser) {
						// User has too many running jobs. Can't start another one.
						runningJobsPerUser.put(uniqueId, newRunningJobs); // refresh
																			// the
																			// list
						logger.warn("Can't start new search, user already has "
								+ numRunningJobs + " jobs running.");
						throw new TooManyRequests("You already have too many running searches. Please wait for some previous searches to complete before starting new ones.");
					}

					// Create a new search object with these parameters and place it
					// in the cache
					job = Job.create(this, user, searchParameters);
					if (job == null) {
						logger.error("search == null, unpossiblez!!!");
					}
					cache.put(job);

					// Update running jobs
					newRunningJobs.add(job);
					runningJobsPerUser.put(uniqueId, newRunningJobs);

					performSearch = true;
				}
			}

			if (performSearch) {
				// Start the search, waiting a short time in case it's a fast search
				job.perform(waitTimeInNonblockingModeMs);
			}
//			else {
//				search.incrementClientsWaiting();
//			}

			// If the search thread threw an exception, rethrow it now.
			if (job.threwException()) {
				job.rethrowException();
			}

			job.incrRef();
			return job;

		} finally {
			//logger.debug("@PERF search EXIT");
		}
	}

	public long getMinFreeMemForSearchMegs() {
		return minFreeMemForSearchMegs;
	}

	public String getParameterDefaultValue(String paramName) {
		String defVal = defaultParameterValues.get(paramName);
		/*
		 * if (defVal == null) { defVal = "";
		 * defaultParameterValues.put(paramName, defVal); }
		 */
		return defVal;
	}

	public static boolean strToBool(String value)
			throws IllegalArgumentException {
		if (value.equals("true") || value.equals("1") || value.equals("yes")
				|| value.equals("on"))
			return true;
		if (value.equals("false") || value.equals("0") || value.equals("no")
				|| value.equals("off"))
			return false;
		throw new IllegalArgumentException("Cannot convert to boolean: "
				+ value);
	}

	public static int strToInt(String value) throws IllegalArgumentException {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Cannot convert to int: "
					+ value);
		}
	}

	/**
	 * Construct a simple error response object.
	 *
	 * @param code
	 *            (string) error code
	 * @param msg
	 *            the error message
	 * @return the data object representing the error message
	 */
	public static DataObject errorObject(String code, String msg) {
		DataObjectMapElement error = new DataObjectMapElement();
		error.put("code", new DataObjectString(code));
		error.put("message", new DataObjectString(msg));
		DataObjectMapElement rv = new DataObjectMapElement();
		rv.put("error", error);
		return rv;
	}

	public TextPattern parsePatt(Searcher searcher, String pattern,
			String language) throws BlsException {
		return parsePatt(searcher, pattern, language, true);
	}

	public TextPattern parsePatt(Searcher searcher, String pattern,
			String language, boolean required) throws BlsException {
		if (pattern == null || pattern.length() == 0) {
			if (required)
				throw new BadRequest("NO_PATTERN_GIVEN",
						"Text search pattern required. Please specify 'patt' parameter.");
			return null; // not required, ok
		}

		if (language.equals("corpusql")) {
			try {
				return CorpusQueryLanguageParser.parse(pattern);
			} catch (ParseException e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in CorpusQL pattern: " + e.getMessage());
			} catch (TokenMgrError e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in CorpusQL pattern: " + e.getMessage());
			}
		} else if (language.equals("contextql")) {
			try {
				CompleteQuery q = ContextualQueryLanguageParser.parse(searcher,
						pattern);
				return q.getContentsQuery();
			} catch (nl.inl.blacklab.queryParser.contextql.TokenMgrError e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in ContextQL pattern: " + e.getMessage());
			} catch (nl.inl.blacklab.queryParser.contextql.ParseException e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in ContextQL pattern: " + e.getMessage());
			}
		} /*else if (language.equals("luceneql")) {
			try {
				String field = searcher.getIndexStructure()
						.getMainContentsField().getName();
				LuceneQueryParser parser = new LuceneQueryParser(
						Version.LUCENE_42, field, searcher.getAnalyzer());
				return parser.parse(pattern);
			} catch (nl.inl.blacklab.queryParser.lucene.ParseException e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in LuceneQL pattern: " + e.getMessage());
			} catch (nl.inl.blacklab.queryParser.lucene.TokenMgrError e) {
				throw new BadRequest("PATT_SYNTAX_ERROR",
						"Syntax error in LuceneQL pattern: " + e.getMessage());
			}
		}*/

		throw new BadRequest("UNKNOWN_PATT_LANG",
				"Unknown pattern language '" + language
						+ "'. Supported: corpusql, contextql, luceneql.");
	}

	public static Query parseFilter(Searcher searcher, String filter,
			String filterLang) throws BlsException {
		return parseFilter(searcher, filter, filterLang, false);
	}

	public static Query parseFilter(Searcher searcher, String filter,
			String filterLang, boolean required) throws BlsException {
		if (filter == null || filter.length() == 0) {
			if (required)
				throw new BadRequest("NO_FILTER_GIVEN",
						"Document filter required. Please specify 'filter' parameter.");
			return null; // not required
		}

		Analyzer analyzer = searcher.getAnalyzer();
		if (filterLang.equals("luceneql")) {
			try {
				QueryParser parser = new QueryParser("", analyzer);
				parser.setAllowLeadingWildcard(true);
				Query query = parser.parse(filter);
				return query;
			} catch (org.apache.lucene.queryparser.classic.ParseException e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing LuceneQL filter query: "
								+ e.getMessage());
			} catch (org.apache.lucene.queryparser.classic.TokenMgrError e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing LuceneQL filter query: "
								+ e.getMessage());
			}
		} else if (filterLang.equals("contextql")) {
			try {
				CompleteQuery q = ContextualQueryLanguageParser.parse(searcher,
						filter);
				return q.getFilterQuery();
			} catch (nl.inl.blacklab.queryParser.contextql.TokenMgrError e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing ContextQL filter query: "
								+ e.getMessage());
			} catch (nl.inl.blacklab.queryParser.contextql.ParseException e) {
				throw new BadRequest("FILTER_SYNTAX_ERROR",
						"Error parsing ContextQL filter query: "
								+ e.getMessage());
			}
		}

		throw new BadRequest("UNKNOWN_FILTER_LANG",
				"Unknown filter language '" + filterLang
						+ "'. Supported: luceneql, contextql.");
	}

	public int getCheckAgainAdviceMinimumMs() {
		return checkAgainAdviceMinimumMs;
	}

	public boolean isDebugMode(String ip) {
		return debugModeIps.contains(ip);
	}

	static void debugWait() throws BlsException {
		// Fake extra search time
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			throw new ServiceUnavailable("Debug wait interrupted");
		}
	}

	public DataFormat getContentsFormat(String indexName) {
		return DataFormat.XML; // could be made configurable
	}

	public int getMaxContextSize() {
		return maxContextSize;
	}

	public synchronized DataObject getCacheStatusDataObject() {
		return cache.getCacheStatusDataObject();
	}

	public synchronized DataObject getCacheContentsDataObject(boolean debugInfo) {
		return cache.getContentsDataObject(debugInfo);
	}

	public int getMaxSnippetSize() {
		return maxSnippetSize;
	}

	public boolean mayOverrideUserId(String ip) {
		return overrideUserIdIps.contains(ip);
	}

	public DataFormat getDefaultOutputType() {
		return defaultOutputType;
	}

	public int getClientCacheTimeSec() {
		return clientCacheTimeSec;
	}

	/**
	 * Give advice for how long to wait to check the status of a search.
	 *
	 * @param search
	 *            the search you want to check the status of
	 * @return how long you should wait before asking again
	 */
	public int getCheckAgainAdviceMs(Job search) {

		// Simple advice algorithm: the longer the search
		// has been running, the less frequently the client
		// should check its progress. Just divide the search time by
		// 5 with a configured minimum.
		int checkAgainAdvice = Math.min(checkAgainAdviceMinimumMs, (int)(search.userWaitTime() * 1000 / checkAgainAdviceDivider));

		return checkAgainAdvice;
	}

	/**
	 * Get maximum allowed value for maxretrieve parameter.
	 *
	 * @return the maximum, or -1 if there's no limit
	 */
	public int getMaxHitsToRetrieveAllowed() {
		return maxHitsToRetrieveAllowed;
	}

	/**
	 * Get maximum allowed value for maxcount parameter.
	 *
	 * @return the maximum, or -1 if there's no limit
	 */
	public int getMaxHitsToCountAllowed() {
		return maxHitsToCountAllowed;
	}

	public int getMaxPageSize() {
		return maxPageSize;
	}

	public int getDefaultPageSize() {
		return defaultPageSize;
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

	public synchronized void clearCache() {
		cache.clearCache();
	}

	/**
	 * Are we allowed to query the list of all document?
	 * (slow for large corpora)
	 * @return true if we are, false if not
	 */
	public static boolean isAllDocsQueryAllowed() {
		return allDocsQueryAllowed;
	}

	/**
	 * Remove a cancelled job from the cache.
	 *
	 * @param job the job to remove
	 */
	public void removeFromCache(Job job) {
		removeFromCache(job);
	}

}
