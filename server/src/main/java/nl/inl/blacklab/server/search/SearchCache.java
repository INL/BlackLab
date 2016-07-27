package nl.inl.blacklab.server.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectList;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.MemoryUtil;
import nl.inl.util.ThreadPriority.Level;
import nl.inl.util.json.JSONObject;

import org.apache.log4j.Logger;

public class SearchCache {
	private static final Logger logger = Logger.getLogger(SearchCache.class);

	/** Max time searches are allowed to run (5 minutes) */
	public static int maxSearchTimeSec = 5 * 60;

	/** @param maxSearchTimeSec Max time searches are allowed to run (default: 300s == 5 minutes) */
	public static void setMaxSearchTimeSec(int maxSearchTimeSec) {
		SearchCache.maxSearchTimeSec = maxSearchTimeSec;
	}

	/** The cached search objects. */
	private Map<String, Job> cachedSearches;

	/** Maximum size in MB to target, or -1 for no limit. NOT IMPLEMENTED YET. */
	private long maxSizeMegs = -1;

	/** Maximum number of searches to cache, or -1 for no limit. Defaults to 100.*/
	private int maxNumberOfJobs = 100;

	/** Maximum age of a cached search in seconds. May be exceeded because it is only cleaned up when
	 *  adding new searches. Defaults to one hour. */
	private int maxJobAgeSec = 3600;

	/** (Estimated) size of the cache. Only updated in removeOldSearches, so may not
	 * always be accurate. */
	private long cacheSizeBytes;

	/** How much free memory we should try to target when cleaning the cache. */
	private long minFreeMemTargetMegs;

	/** If we're below target mem, how many jobs should we get rid of each time we add something to the cache? */
	private int numberOfJobsToPurgeWhenBelowTargetMem;

	private int maxConcurrentSearches = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

	private boolean autoDetectMaxConcurrent = true;

	private int maxPausedSearches = 10;

	/**
	 * Initialize the cache.
	 *
	 * @param settings cache settings
	 */
	public SearchCache(JSONObject settings) {
		cachedSearches = new HashMap<>();
		maxJobAgeSec = JsonUtil.getIntProp(settings, "maxJobAgeSec", 3600);
		maxNumberOfJobs = JsonUtil.getIntProp(settings, "maxNumberOfJobs", 20);
		maxSizeMegs = JsonUtil.getIntProp(settings, "maxSizeMegs", -1);
		minFreeMemTargetMegs = JsonUtil.getIntProp(settings, "targetFreeMemMegs", 100);
		numberOfJobsToPurgeWhenBelowTargetMem = JsonUtil.getIntProp(settings, "numberOfJobsToPurgeWhenBelowTargetMem", 100);
	}

	public SearchCache() {
		cachedSearches = new HashMap<>();
		maxJobAgeSec = 3600;
		maxNumberOfJobs = 20;
		maxSizeMegs = -1;
		minFreeMemTargetMegs = 100;
		numberOfJobsToPurgeWhenBelowTargetMem = 100;
	}

	/**
	 * Get a search from the cache if present.
	 *
	 * @param jobDesc the search parameters
	 * @return the Search if found, or null if not
	 */
	public Job get(Job.Description jobDesc) {
		Job search = cachedSearches.get(jobDesc.uniqueIdentifier());
		if (search == null) {
			//logger.debug("Cache miss: " + jobDesc);
		} else {
			//logger.debug("Cache hit: " + jobDesc);
			search.resetLastAccessed();
		}
		return search;
	}

	/** Put a search in the cache.
	 *
	 * Also cleans older searches from the cache if necessary.
	 *
	 * @param search the search object
	 */
	public void put(Job search) {
		if (maxNumberOfJobs <= 0)
			return;

		removeOldSearches();

		performLoadManagement(search);

		// Search already in cache?
		String uniqueIdentifier = search.getDescription().uniqueIdentifier();
		if (cachedSearches.containsKey(uniqueIdentifier)) {
			if (cachedSearches.get(uniqueIdentifier) != search) {
				throw new RuntimeException("Cache already contains different search object!");
			}
			// Same object already in cache, do nothing
			logger.debug("Same object put in cache twice: " + uniqueIdentifier);
			return;
		}

		// Put search in cache
		//logger.debug("Put in cache: " + uniqueIdentifier);
		cachedSearches.put(uniqueIdentifier, search);
		search.incrRef();
	}

	/**
	 * Remove all cache entries for the specified index.
	 *
	 * @param indexName the index
	 */
	public void clearCacheForIndex(String indexName) {
		// Iterate over the entries and remove the ones in the specified index
		Iterator<Map.Entry<String, Job>> it = cachedSearches.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, Job> entry = it.next();
			if (entry.getValue().getDescription().getIndexName().equals(indexName)) {
				entry.getValue().decrRef();
				it.remove();
			}
		}
	}

	/**
	 * Get rid of all the cached Searches.
	 */
	public void clearCache() {
		clearCache(false);
	}

	/**
	 * Get rid of all the cached Searches.
	 *
	 * @param cancelRunning if true, cancels all running searches as well.
	 */
	public void clearCache(boolean cancelRunning) {
		for (Job cachedSearch: cachedSearches.values()) {
			if (!cachedSearch.finished())
				cachedSearch.cancelJob();
			cachedSearch.decrRef();
		}
		cachedSearches.clear();
		logger.debug("Cache cleared.");
	}

	/**
	 * If the cache exceeds the given parameters, clean it up by
	 * removing less recently used searches.
	 */
	void removeOldSearches() {

		// Sort cache by last access time
		List<Job> lastAccessOrder = new ArrayList<>(cachedSearches.values());
		Collections.sort(lastAccessOrder); // sort on worthiness

		calculateSizeBytes(lastAccessOrder);

		// If we're low on memory, always remove a few searches from cache.
		int minSearchesToRemove = 0;
		long freeMegs = MemoryUtil.getFree() / 1000000;
		if (freeMegs < minFreeMemTargetMegs) {
			minSearchesToRemove = numberOfJobsToPurgeWhenBelowTargetMem; // arbitrary, number but will keep on being removed every call until enough free mem has been reclaimed
			logger.debug("Not enough free mem, will remove some searches.");
		}

		// Look at searches from least worthy to worthiest.
		// Get rid of old searches
		boolean lookAtCacheSizeAndSearchAccessTime = true;
		for (int i = lastAccessOrder.size() - 1; i >= 0; i--) {
			Job search = lastAccessOrder.get(i);

			if (!search.finished() && search.userWaitTime() > maxSearchTimeSec) {
				// Search is taking too long. Cancel it.
				logger.debug("Search is taking too long, cancelling: " + search);
				abortSearch(search);

			} else {
				boolean removeBecauseOfCacheSizeOrAge = false;
				if (lookAtCacheSizeAndSearchAccessTime) {
					boolean isCacheTooBig = cacheTooBig();
					boolean isSearchTooOld = false;
					if (!isCacheTooBig)
						isSearchTooOld = searchTooOld(search);
					removeBecauseOfCacheSizeOrAge = isCacheTooBig || isSearchTooOld;
				}
				if (minSearchesToRemove > 0 || removeBecauseOfCacheSizeOrAge) {
					// Search is too old or cache is too big. Keep removing searches until that's no longer the case
					//logger.debug("Remove from cache: " + search);
					removeFromCache(search);

					minSearchesToRemove--;
				} else {
					// Cache is no longer too big and these searches are not too old. Stop checking that,
					// just check for long-running searches
					lookAtCacheSizeAndSearchAccessTime = false;
				}
			}
		}
		// NOTE: we used to hint the Java GC to run, but this caused severe
		// slowdowns. It's better to rely on the incremental garbage collection.
	}

	private long calculateSizeBytes(Collection<Job> collection) {
		// Estimate the total cache size
		cacheSizeBytes = 0;
		for (Job search: collection) {
			cacheSizeBytes += search.estimateSizeBytes();
		}
		return cacheSizeBytes;
	}

	/**
	 * Checks if the cache size in bytes or number of searches is too big.
	 *
	 * Only applies if maxSizeBytes >= 0 or maxSizeSearcher >= 0.
	 *
	 * @return true iff the cache is too big.
	 */
	private boolean cacheTooBig() {
		boolean tooManySearches = maxNumberOfJobs >= 0
				&& cachedSearches.size() > maxNumberOfJobs;
		long cacheSizeMegs = cacheSizeBytes / 1000000;
		boolean tooMuchMemory = maxSizeMegs >= 0 && cacheSizeMegs > maxSizeMegs;
		return tooManySearches || tooMuchMemory;
	}

	/**
	 * Checks if the search is too old to remain in cache.
	 *
	 * Only applies if maxJobAgeSec >= 0.
	 *
	 * @param search the search to check
	 * @return true iff the search is too old
	 */
	private boolean searchTooOld(Job search) {
		boolean tooOld = maxJobAgeSec >= 0 && search.cacheAge() > maxJobAgeSec;
		return tooOld;
	}

	/**
	 * Return the maximum size of the cache to target, in bytes.
	 *
	 * @return targeted max. size of the cache in bytes, or -1 for no limit
	 */
	public long getMaxSizeBytes() {
		return maxSizeMegs;
	}

	/**
	 * Set the maximum size of the cache to target, in bytes.
	 *
	 * NOTE: the maximum size is checked based on a rough estimate of the
	 * memory consumed by each search. Also, the specified value may be exceeded
	 * because Search objects are added to the cache before the search is executed,
	 * so they grow in size. Choose a conservative size and monitor memory usage in
	 * practice.
	 *
	 * @param maxSizeBytes targeted max. size of the cache in bytes, or -1 for no limit
	 */
	public void setMaxSizeBytes(long maxSizeBytes) {
		this.maxSizeMegs = maxSizeBytes;
		removeOldSearches();
	}

	/**
	 * Return the maximum size of the cache in number of searches.
	 * @return the maximum size, or -1 for no limit
	 */
	public int getMaxJobsToCache() {
		return maxNumberOfJobs;
	}

	/**
	 * Set the maximum size of the cache in number of searches.
	 * @param maxJobs the maximum size, or -1 for no limit
	 */
	public void setMaxJobsToCache(int maxJobs) {
		this.maxNumberOfJobs = maxJobs;
		removeOldSearches();
	}

	/**
	 * Return the maximum age of a search in the cache.
	 *
	 * The age is defined as the period of time since the last access.
	 *
	 * @return the maximum age, or -1 for no limit
	 */
	public int getMaxJobAgeSec() {
		return maxJobAgeSec;
	}

	/**
	 * Set the maximum age of a search in the cache.
	 *
	 * The age is defined as the period of time since the last access.
	 *
	 * @param maxJobAgeSec the maximum age, or -1 for no limit
	 */
	public void setMaxJobAgeSec(int maxJobAgeSec) {
		this.maxJobAgeSec = maxJobAgeSec;
	}

	public long getSizeBytes() {
		return calculateSizeBytes(cachedSearches.values());
	}

	public int getNumberOfSearches() {
		return cachedSearches.size();
	}

	public int numberOfRunningSearches() {
		int n = 0;
		for (Job job: cachedSearches.values()) {
			if (!job.finished() && !job.isWaitingForOtherJob() && job.getPriorityLevel() != Level.PAUSED) {
				n++;
			}
		}
		return n;
	}

	public void setMinFreeMemTargetBytes(long minFreeMemTargetBytes) {
		this.minFreeMemTargetMegs = minFreeMemTargetBytes;
	}

	public DataObject getCacheStatusDataObject() {
		DataObjectMapElement doCache = new DataObjectMapElement();
		doCache.put("maxSizeBytes", getMaxSizeBytes());
		doCache.put("maxNumberOfSearches", getMaxJobsToCache());
		doCache.put("maxSearchAgeSec", getMaxJobAgeSec());
		doCache.put("sizeBytes", getSizeBytes());
		doCache.put("numberOfSearches", getNumberOfSearches());
		return doCache;
	}

	public DataObject getContentsDataObject(boolean debugInfo) throws BlsException {
		DataObjectList doCacheContents = new DataObjectList("job");
		for (Job job: cachedSearches.values()) {
			doCacheContents.add(job.toDataObject(debugInfo));
		}
		return doCacheContents;
	}

	/**
	 * What we can do to a query in response to the server load.
	 */
	enum ServerLoadQueryAction {
		RUN_NORMALLY,      // no action
		PAUSE,             // pause search
		ABORT,             // abort search / refuse to start new search
		REMOVE_FROM_CACHE, // discard results from cache
	}

	/**
	 * Evaluate what we need to do (if anything) with each search given the
	 * current server load.
	 *
	 * @param newSearch the new search just started, or null if none.
	 */
	void performLoadManagement(Job newSearch) {

		if (autoDetectMaxConcurrent) {
			// Autodetect number of CPUs
			int n = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
			if (n != maxConcurrentSearches) {
				logger.debug("maxConcurrentSearches autodetect: changed from " + maxConcurrentSearches + " to " + n);
				maxConcurrentSearches = n;
			}
		}

		List<Job> searches = new ArrayList<>(cachedSearches.values());

		// Sort the searches based on descending "worthiness"
		Collections.sort(searches);

		int coresLeft = maxConcurrentSearches;
		int pauseSlotsLeft = maxPausedSearches;
		//logger.debug("=== LOADMGR: START. cores=" + coresLeft + ", pauseSlots=" + pauseSlotsLeft);
		//int cacheSlotsLeft = maxNumberOfJobs;
		for (Job search: searches) {
			if (search.finished()) {
				// Finished search. Keep in cache?

				// NOTE: we'll leave this to removeOldSearches() for now.
				// Later we'll integrate the two.
				//logger.debug("LOADMGR: search is finished, no problem: " + search);

			} else if (search.isWaitingForOtherJob()) {
				// Waiting, not taking up any CPU. Can run normally, but doesn't take a core.
				//logger.debug("LOADMGR: search is waiting for other job: " + search);
				applyAction(search, ServerLoadQueryAction.RUN_NORMALLY);
			} else {
				// Running search. Run, pause or abort?
				if (coresLeft > 0) {
					// A core is available. Run the search.
					coresLeft--;
					applyAction(search, ServerLoadQueryAction.RUN_NORMALLY);
					//logger.debug("LOADMGR: search is running, ok: " + search);
				} else if (pauseSlotsLeft > 0) {
					// No cores, but a pause slot is left. Pause it.
					logger.debug("LOADMGR: no cores left for search: " + search);
					pauseSlotsLeft--;
					applyAction(search, ServerLoadQueryAction.PAUSE);
				} else {
					// No cores or pause slots. Abort the search.
					logger.debug("LOADMGR: no pause slots left, aborting: " + search);
					applyAction(search, ServerLoadQueryAction.ABORT);
				}
			}
		}
		//logger.debug("=== LOADMGR: END.");
	}

	/**
	 * Apply one of the load managing actions to a search.
	 * @param m
	 *
	 * @param search the search
	 * @param action the action to apply
	 */
	private void applyAction(Job search, ServerLoadQueryAction action) {
		// See what to do with the current search
		switch(action) {
		case RUN_NORMALLY:
			// "No action": if paused, keep paused; if running, run normally (not low prio)
			if (search.getPriorityLevel() != Level.RUNNING) {
				logger.debug("LOADMGR: Resuming search: " + search);
				search.setPriorityLevel(Level.RUNNING);
			}
			break;
		case PAUSE:
			if (search.getPriorityLevel() != Level.PAUSED) {
				logger.debug("LOADMGR: Pausing search: " + search + " (was: "+search.getPriorityLevel()+")");
				search.setPriorityLevel(Level.PAUSED);
			}
			break;
		case ABORT:
			if (!search.finished()) {
				// TODO: Maybe we should blacklist certain searches for a time?
				logger.warn("LOADMGR: Aborting search: " + search);
				abortSearch(search);
			}
			break;
		case REMOVE_FROM_CACHE:
			logger.debug("LOADMGR: Discarding from cache: " + search);
			removeFromCache(search);
			break;
		}
	}

	private void removeFromCache(Job search) {
		cachedSearches.remove(search.getDescription().uniqueIdentifier());
		search.decrRef();
		cacheSizeBytes -= search.estimateSizeBytes();
	}

	private void abortSearch(Job search) {
		search.cancelJob();
		removeFromCache(search);
	}

	public void setServerLoadOptions(JSONObject jsonServerLoad) {
		maxConcurrentSearches = 1;
		if (jsonServerLoad != null)
			maxConcurrentSearches = JsonUtil.getIntProp(jsonServerLoad, "maxConcurrentSearches", -1);
		autoDetectMaxConcurrent = maxConcurrentSearches <= 0;
		if (autoDetectMaxConcurrent) {
			maxConcurrentSearches = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
			logger.debug("Autodetect maxConcurrentSearches: " + maxConcurrentSearches);
		}

		maxPausedSearches = 10;
		if (jsonServerLoad != null)
			maxPausedSearches = JsonUtil.getIntProp(jsonServerLoad, "maxPausedSearches", 10);
	}
}
