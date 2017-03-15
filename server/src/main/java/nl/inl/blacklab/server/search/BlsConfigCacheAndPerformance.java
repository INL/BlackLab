package nl.inl.blacklab.server.search;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import nl.inl.blacklab.server.util.JsonUtil;

public class BlsConfigCacheAndPerformance {

	private static final Logger logger = LogManager.getLogger(BlsConfigCacheAndPerformance.class);

	/** Maximum size in MB to target, or -1 for no limit. NOT IMPLEMENTED YET. */
	private long maxSizeMegs = -1;

	/** Maximum number of searches to cache, or -1 for no limit. Defaults to 100.*/
	private int maxNumberOfJobs = 100;

	/** Maximum age of a cached search in seconds. May be exceeded because it is only cleaned up when
	 *  adding new searches. Defaults to one hour. */
	private int maxJobAgeSec = 3600;

	/** How much free memory we should try to target when cleaning the cache. */
	private long minFreeMemTargetMegs;

	/** If we're below target mem, how many jobs should we get rid of each time we add something to the cache? */
	private int numberOfJobsToPurgeWhenBelowTargetMem;

	private int maxConcurrentSearches = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);

	private boolean autoDetectMaxConcurrent = true;

	private int maxPausedSearches = 10;

	/** Max time searches are allowed to run (5 minutes) */
	private int maxSearchTimeSec = 5 * 60;

	/** After how many seconds should we pause counting if client isn't checking the status anymore? */
	private int abandonedCountPauseTimeSec = 10;

	/** After how many seconds should we abort counting if client isn't checking the status anymore? */
	private int abandonedCountAbortTimeSec = 600;

	/**
	 * If enabled, this makes sure the SearchCache will follow the behaviour
	 * rules set in blacklab-server.json to lowprio/pause searches in certain
	 * situations under certain loads.
	 * (EXPERIMENTAL)
	 */
	private boolean enableThreadPriority = false;

	/** Minimum amount of free memory (MB) to start a new search. [50] */
	private int minFreeMemForSearchMegs;

	public int getMinFreeMemForSearchMegs() {
		return minFreeMemForSearchMegs;
	}

	public int getMaxRunningJobsPerUser() {
		return maxRunningJobsPerUser;
	}

	public int getClientCacheTimeSec() {
		return clientCacheTimeSec;
	}

	/**
	 * Maximum number of simultaneously running jobs started by the same user.
	 * [20] Please note that a search may start 2-4 jobs, so don't set this too
	 * low. This is just meant to prevent over-eager scripts and other abuse.
	 * Regular users should never hit this limit.
	 */
	private int maxRunningJobsPerUser;

	/**
	 * How long the client may used a cached version of the results we give
	 * them. This is used to write HTTP cache headers. A value of an hour or so
	 * seems reasonable.
	 */
	private int clientCacheTimeSec;

	public BlsConfigCacheAndPerformance(JSONObject settings) {
		this();

		if (settings != null) {
			minFreeMemForSearchMegs = JsonUtil.getIntProp(settings, "minFreeMemForSearchMegs", 50);
			maxRunningJobsPerUser = JsonUtil.getIntProp(settings, "maxRunningJobsPerUser", 20);
			clientCacheTimeSec = JsonUtil.getIntProp(settings, "clientCacheTimeSec", 3600);
		} else {
			// Set default values
			minFreeMemForSearchMegs = 50;
			maxRunningJobsPerUser = 20;
			clientCacheTimeSec = 3600;
		}

		JSONObject cacheSettings = null;
		if (settings != null && settings.has("cache"))
			cacheSettings = settings.getJSONObject("cache");
		if (cacheSettings != null) {
			maxJobAgeSec = JsonUtil.getIntProp(settings, "maxJobAgeSec", 3600);
			maxNumberOfJobs = JsonUtil.getIntProp(settings, "maxNumberOfJobs", 20);
			maxSizeMegs = JsonUtil.getIntProp(settings, "maxSizeMegs", -1);
			minFreeMemTargetMegs = JsonUtil.getIntProp(settings, "targetFreeMemMegs", 100);
			numberOfJobsToPurgeWhenBelowTargetMem = JsonUtil.getIntProp(settings, "numberOfJobsToPurgeWhenBelowTargetMem", 2);
		}

		JSONObject serverLoadSettings = null;
		if (settings != null && settings.has("serverLoad")) {
			// Load manager stuff (experimental)
			serverLoadSettings = settings.getJSONObject("serverLoad");

			enableThreadPriority = true; // EXPERIMENTAL
		}
		if (serverLoadSettings != null) {
			maxConcurrentSearches = 1;
			maxConcurrentSearches = JsonUtil.getIntProp(serverLoadSettings, "maxConcurrentSearches", -1);
			autoDetectMaxConcurrent = maxConcurrentSearches <= 0;
			if (autoDetectMaxConcurrent) {
				maxConcurrentSearches = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
				logger.debug("Autodetect maxConcurrentSearches: " + maxConcurrentSearches);
			}

			maxPausedSearches = JsonUtil.getIntProp(serverLoadSettings, "maxPausedSearches", 10);

			abandonedCountPauseTimeSec = JsonUtil.getIntProp(serverLoadSettings, "abandonedCountPauseTimeSec", 10);
			abandonedCountAbortTimeSec = JsonUtil.getIntProp(serverLoadSettings, "abandonedCountAbortTimeSec", 600);
		}

	}

	public BlsConfigCacheAndPerformance() {
		maxJobAgeSec = 3600;
		maxNumberOfJobs = 20;
		maxSizeMegs = -1;
		minFreeMemTargetMegs = 100;
		numberOfJobsToPurgeWhenBelowTargetMem = 100;
	}

	public long getMaxSizeMegs() {
		return maxSizeMegs;
	}

	public int getMaxNumberOfJobs() {
		return maxNumberOfJobs;
	}

	public int getMaxJobAgeSec() {
		return maxJobAgeSec;
	}

	public long getMinFreeMemTargetMegs() {
		return minFreeMemTargetMegs;
	}

	public int getNumberOfJobsToPurgeWhenBelowTargetMem() {
		return numberOfJobsToPurgeWhenBelowTargetMem;
	}

	public int getMaxConcurrentSearches() {
		return maxConcurrentSearches;
	}

	public boolean shouldAutoDetectMaxConcurrent() {
		return autoDetectMaxConcurrent;
	}

	public int getMaxPausedSearches() {
		return maxPausedSearches;
	}

	public int getMaxSearchTimeSec() {
		return maxSearchTimeSec;
	}

	public int getAbandonedCountPauseTimeSec() {
		return abandonedCountPauseTimeSec;
	}

	public int getAbandonedCountAbortTimeSec() {
		return abandonedCountAbortTimeSec;
	}

	public void autoAdjustMaxConcurrent() {
		int n = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
		if (n != maxConcurrentSearches) {
			logger.debug("maxConcurrentSearches autodetect: changed from " + maxConcurrentSearches + " to " + n);
			maxConcurrentSearches = n;
		}
	}

	public boolean enableThreadPriority() {
		return enableThreadPriority;
	}

}