package nl.inl.blacklab.server.search;

import org.apache.log4j.Logger;

import nl.inl.blacklab.server.util.JsonUtil;
import nl.inl.util.json.JSONObject;

public class BlsConfigCacheAndPerformance {

	private static final Logger logger = Logger.getLogger(BlsConfigCacheAndPerformance.class);

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

	public BlsConfigCacheAndPerformance(JSONObject settings) {
		this();
		if (settings != null) {
			maxJobAgeSec = JsonUtil.getIntProp(settings, "maxJobAgeSec", 3600);
			maxNumberOfJobs = JsonUtil.getIntProp(settings, "maxNumberOfJobs", 20);
			maxSizeMegs = JsonUtil.getIntProp(settings, "maxSizeMegs", -1);
			minFreeMemTargetMegs = JsonUtil.getIntProp(settings, "targetFreeMemMegs", 100);
			numberOfJobsToPurgeWhenBelowTargetMem = JsonUtil.getIntProp(settings, "numberOfJobsToPurgeWhenBelowTargetMem", 100);
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

	public void autoAdjustMaxConcurrent() {
		int n = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
		if (n != maxConcurrentSearches) {
			logger.debug("maxConcurrentSearches autodetect: changed from " + maxConcurrentSearches + " to " + n);
			maxConcurrentSearches = n;
		}
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