package nl.inl.blacklab.server.search;

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

import org.apache.log4j.Logger;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.exceptions.TooManyRequests;
import nl.inl.blacklab.server.jobs.Job;
import nl.inl.blacklab.server.jobs.JobDescription;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.MemoryUtil;
import nl.inl.util.ThreadPriority;
import nl.inl.util.ThreadPriority.Level;

public class SearchCache {
	private static final Logger logger = Logger.getLogger(SearchCache.class);

	/**
	 * What we can do to a query in response to the server load.
	 */
	private static enum ServerLoadQueryAction {
		RUN_NORMALLY, // no action
		PAUSE, // pause search
		ABORT, // abort search / refuse to start new search
		REMOVE_FROM_CACHE, // discard results from cache
	}

	/** Our configuration */
	private BlsConfigCacheAndPerformance cacheConfig;

	/** The cached search objects. */
	private Map<String, Job> cachedSearches;

	/** (Estimated) size of the cache. Only updated in removeOldSearches, so may not
	 * always be accurate. */
	private long cacheSizeBytes;

	/**
	 * A thread that ensures load management continues even if
	 * no new requests are coming in.
	 */
	private Thread loadManagerThread;

	/** Keeps track of running jobs per user, so we can limit this. */
	private Map<String, Set<Job>> runningJobsPerUser = new HashMap<>();

	private SearchManager searchMan;

	/**
	 * Initialize the cache.
	 *
	 * @param searchMan search manager object
	 * @param config our config
	 */
	public SearchCache(SearchManager searchMan, BlsConfig config) {
		this.searchMan = searchMan;

		this.cacheConfig = config.getCacheConfig();

		// Make sure long operations yield their thread occasionally,
		// and automatically abort really long operations.
		ThreadPriority.setEnabled(cacheConfig.enableThreadPriority());

		cachedSearches = new HashMap<>();

		loadManagerThread = new LoadManagerThread(this);
		loadManagerThread.start();

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

	public void cleanup() {
		// Stop the load manager thread
		loadManagerThread.interrupt();
		loadManagerThread = null;

		clearCache(true);

		searchMan = null;
	}

	public void dataStreamCacheStatus(DataStream ds) {
		long maxSizeMegs = cacheConfig.getMaxSizeMegs();
		long maxSizeBytes = maxSizeMegs < 0 ? -1 : maxSizeMegs * 1000 * 1000;
		ds.startMap()
			.entry("maxSizeBytes", maxSizeBytes)
			.entry("maxNumberOfSearches", cacheConfig.getMaxNumberOfJobs())
			.entry("maxSearchAgeSec", cacheConfig.getMaxJobAgeSec())
			.entry("sizeBytes", calculateSizeBytes(cachedSearches.values()))
			.entry("numberOfSearches", cachedSearches.size())
		.endMap();
	}

	public void dataStreamContents(DataStream ds, boolean debugInfo) {
		ds.startList();
		for (Job job: cachedSearches.values()) {
			ds.startItem("job");
			job.dataStream(ds, debugInfo);
			ds.endItem();
		}
		ds.endList();
	}

	/**
	 * Start a new search or return an existing Search object corresponding to
	 * these search parameters.
	 *
	 * @param user
	 *            user creating the job
	 * @param jobDesc
	 *            the search parameters
	 * @param block if true, wait until job is done
	 * @return a Search object corresponding to these parameters
	 * @throws BlsException
	 *             if the query couldn't be executed
	 */
	public Job search(User user, JobDescription jobDesc, boolean block) throws BlsException {
		// Search the cache / running jobs for this search, create new if not found.
		boolean performSearch = false;
		Job job;
		synchronized (this) {
			job = get(jobDesc);
			if (job == null) {
				// Not found in cache

				// Do we have enough memory to start a new search?
				checkFreeMemory();

				// Check what jobs this user has running
				// Check if search allowed, and update running jobs for user
				Set<Job> runningJobs = updateRunningJobs(user.uniqueId());
				if (runningJobs.size() >= cacheConfig.getMaxRunningJobsPerUser()) {
					// User has too many running jobs. Can't start another one.
					logger.warn("Can't start new search, user already has " + runningJobs.size() + " jobs running.");
					throw new TooManyRequests(
							"You already have too many running searches. Please wait for some previous searches to complete before starting new ones.");
				}

				// Create a new search object with these parameters and place it in the cache
				job = jobDesc.createJob(searchMan, user);
				job.incrRef();
				put(job);
				runningJobs.add(job);
				performSearch = true;
			}
		}

		if (performSearch) {
			// Start the search, waiting a short time in case it's a fast search
			job.perform(500); // hardcoded because we are going to remove nonblocking mode soon
		}

		// If the search thread threw an exception, rethrow it now.
		if (job.threwException()) {
			job.rethrowException();
		}

		if (block) {
			job.waitUntilFinished(cacheConfig.getMaxSearchTimeSec() * 1000);
			if (!job.finished()) {
				throw new ServiceUnavailable("Search took too long, cancelled.");
			}
		}
		return job;
	}

	/**
	 * Get a search from the cache if present.
	 *
	 * @param jobDesc the search parameters
	 * @return the Search if found, or null if not
	 */
	private Job get(JobDescription jobDesc) {
		Job search = cachedSearches.get(jobDesc.uniqueIdentifier());
		if (search == null) {
			// logger.debug("Cache miss: " + jobDesc);
		} else {
			// logger.debug("Cache hit: " + jobDesc);
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
	private void put(Job search) {
		if (cacheConfig.getMaxNumberOfJobs() <= 0)
			return;

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
		// logger.debug("Put in cache: " + uniqueIdentifier);
		cachedSearches.put(uniqueIdentifier, search);
		search.incrRef();
	}

	/**
	 * Get rid of all the cached Searches.
	 *
	 * @param cancelRunning if true, cancels all running searches as well.
	 */
	private void clearCache(boolean cancelRunning) {
		for (Job cachedSearch: cachedSearches.values()) {
			if (!cachedSearch.finished())
				cachedSearch.cancelJob();
			cachedSearch.decrRef();
		}
		cachedSearches.clear();
		logger.debug("Cache cleared.");
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
		boolean tooManySearches = cacheConfig.getMaxNumberOfJobs() >= 0 && cachedSearches.size() > cacheConfig.getMaxNumberOfJobs();
		long cacheSizeMegs = cacheSizeBytes / 1000000;
		boolean tooMuchMemory = cacheConfig.getMaxSizeMegs() >= 0 && cacheSizeMegs > cacheConfig.getMaxSizeMegs();
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
		boolean tooOld = cacheConfig.getMaxJobAgeSec() >= 0 && search.cacheAge() > cacheConfig.getMaxJobAgeSec();
		return tooOld;
	}

	/**
	 * Evaluate what we need to do (if anything) with each search given the
	 * current server load.
	 *
	 * @param newSearch the new search just started, or null if none.
	 */
	void performLoadManagement(Job newSearch) {

		if (cacheConfig.shouldAutoDetectMaxConcurrent()) {
			// Autodetect number of CPUs
			cacheConfig.autoAdjustMaxConcurrent();
		}

		for (Entry<String, Job> e: cachedSearches.entrySet()) {
			String key = e.getKey();
			Job job = e.getValue();
			String uniqId = job.getDescription().uniqueIdentifier();
			if (!key.equals(uniqId)) {
				logger.error("### Cached job's key has changed. OLD=" + key + ", NEW=" + uniqId);
			}
		}

		List<Job> searches = new ArrayList<>(cachedSearches.values());

		// Sort the searches based on descending "worthiness"
		Collections.sort(searches);

		//------------------
		// STEP 1: remove least worthy, finished searches from cache

		calculateSizeBytes(searches);

		// If we're low on memory, always remove a few searches from cache.
		int minSearchesToRemove = 0;
		long freeMegs = MemoryUtil.getFree() / 1000000;
		if (freeMegs < cacheConfig.getMinFreeMemTargetMegs()) {
			// pretty arbitrary number, but will keep on being removed every
			// call until enough free mem has been reclaimed
			minSearchesToRemove = cacheConfig.getNumberOfJobsToPurgeWhenBelowTargetMem();
		}

		// Look at searches from least worthy to worthiest.
		// Get rid of old searches
		List<Job> removed = new ArrayList<>();
		boolean lookAtCacheSizeAndSearchAccessTime = true;
		for (int i = searches.size() - 1; i >= 0; i--) {
			Job search1 = searches.get(i);

			if (!search1.finished() && search1.userWaitTime() > cacheConfig.getMaxSearchTimeSec()) {
				// Search is taking too long. Cancel it.
				logger.debug("Search is taking too long, cancelling: " + search1);
				abortSearch(search1);
				removed.add(search1);

			} else {
				boolean removeBecauseOfCacheSizeOrAge = false;
				boolean isCacheTooBig = false;
				boolean isSearchTooOld = false;
				if (lookAtCacheSizeAndSearchAccessTime) {
					isCacheTooBig = cacheTooBig();
					isSearchTooOld = false;
					if (!isCacheTooBig)
						isSearchTooOld = searchTooOld(search1);
					removeBecauseOfCacheSizeOrAge = isCacheTooBig || isSearchTooOld;
				}
				if (minSearchesToRemove > 0 || removeBecauseOfCacheSizeOrAge) {
					// Search is too old or cache is too big. Keep removing searches until that's no
					// longer the case
					// logger.debug("Remove from cache: " + search);
					if (minSearchesToRemove > 0)
						logger.debug("Not enough free mem, removing: " + search1);
					else if (isCacheTooBig)
						logger.debug("Cache too large, removing: " + search1);
					else
						logger.debug("Searchjob too old, removing: " + search1);
					removeFromCache(search1);
					removed.add(search1);

					minSearchesToRemove--;
				} else {
					// Cache is no longer too big and these searches are not too old. Stop checking
					// that,
					// just check for long-running searches
					lookAtCacheSizeAndSearchAccessTime = false;
				}
			}
		}
		// Make sure we don't look at the searches we removed again in the next step
		for (Job r: removed) {
			searches.remove(r);
		}
		// NOTE: we used to hint the Java GC to run, but this caused severe
		// slowdowns. It's better to rely on the incremental garbage collection.

		//------------------
		// STEP 2: make sure the most worthy searches get the CPU, and pause
		//         any others to avoid bringing down the server.

		int coresLeft = cacheConfig.getMaxConcurrentSearches();
		int pauseSlotsLeft = cacheConfig.getMaxPausedSearches();
		for (Job search: searches) {
			if (search.finished()) {
				// Finished search. Keep in cache?

				// NOTE: we'll leave this to removeOldSearches() for now.
				// Later we'll integrate the two.
			} else if (search.isWaitingForOtherJob()) {
				// Waiting, not taking up any CPU. Can run normally, but doesn't take a core.
				applyAction(search, ServerLoadQueryAction.RUN_NORMALLY);
			} else {
				// Running search. Run, pause or abort?
				if (coresLeft > 0) {
					// A core is available. Run the search.
					coresLeft--;
					applyAction(search, ServerLoadQueryAction.RUN_NORMALLY);
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
		switch (action) {
		case RUN_NORMALLY:
			// "No action": if paused, keep paused; if running, run normally (not low prio)
			if (search.getPriorityLevel() != Level.RUNNING) {
				logger.debug("LOADMGR: Resuming search: " + search);
				search.setPriorityLevel(Level.RUNNING);
			}
			break;
		case PAUSE:
			if (search.getPriorityLevel() != Level.PAUSED) {
				logger.debug("LOADMGR: Pausing search: " + search + " (was: " + search.getPriorityLevel() + ")");
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
		String identifier = search.getDescription().uniqueIdentifier();
		Job removed = cachedSearches.remove(identifier);
		if (removed == null) {
			logger.error("Tried to remove search, but not found: " + identifier);
		}
		search.decrRef();
		cacheSizeBytes -= search.estimateSizeBytes();
	}

	private void abortSearch(Job search) {
		search.cancelJob();
		removeFromCache(search);
	}

	private Set<Job> updateRunningJobs(String uniqueId) {
		Set<Job> runningJobs = runningJobsPerUser.get(uniqueId);
		Set<Job> newRunningJobs = new HashSet<>();
		if (runningJobs != null) {
			for (Job runningJob: runningJobs) {
				if (!runningJob.finished()) {
					newRunningJobs.add(runningJob);
				}
			}
		}
		runningJobsPerUser.put(uniqueId, newRunningJobs);
		return newRunningJobs;
	}

	private void checkFreeMemory() throws ServiceUnavailable {
		long freeMegs = MemoryUtil.getFree() / 1000000;
		if (freeMegs < cacheConfig.getMinFreeMemForSearchMegs()) {
			performLoadManagement(null); //removeOldSearches(); // try to free up space for next search
			logger.warn(
					"Can't start new search, not enough memory (" + freeMegs + "M < " + cacheConfig.getMinFreeMemForSearchMegs() + "M)");
			logger.warn("(NOTE: make sure Tomcat's max heap mem is set to an appropriate value!)");
			throw new ServiceUnavailable("The server seems to be under heavy load right now. Please try again later.");
		}
		// logger.debug("Enough free memory: " + freeMegs + "M");
	}

}
