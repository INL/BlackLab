package nl.inl.blacklab.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BLSConfigPerformance {

    static final int MAX_CONCURRENT_SEARCHES_AT_LEAST = 4;

    private static final Logger logger = LogManager.getLogger(BLSConfigPerformance.class);

    boolean autodetectMaxConcurrent = false;

    int maxConcurrentSearches = 4;

    int maxThreadsPerSearch = 2;

    int abandonedCountAbortTimeSec = 30;

    int maxRunningJobsPerUser = 10;

    public int getMaxConcurrentSearches() {
        return maxConcurrentSearches;
    }

    public void setMaxConcurrentSearches(int maxConcurrentSearches) {
        this.maxConcurrentSearches = maxConcurrentSearches;
        if (maxConcurrentSearches <= 0) {
            int n = Math.max(Runtime.getRuntime().availableProcessors(), MAX_CONCURRENT_SEARCHES_AT_LEAST);
            logger.debug("maxConcurrentSearches autodetect: setting to " + n);
            maxConcurrentSearches = n;
        }
    }

    public int getMaxThreadsPerSearch() {
        return maxThreadsPerSearch;
    }

    public void setMaxThreadsPerSearch(int maxThreadsPerSearch) {
        this.maxThreadsPerSearch = maxThreadsPerSearch;
    }

    @Deprecated
    public int getMaxPausedSearches() {
        return 0; //maxPausedSearches;
    }

    @Deprecated
    public void setMaxPausedSearches(int maxPausedSearches) {
        //this.maxPausedSearches = maxPausedSearches;
    }

    public int getMaxRunningJobsPerUser() {
        return maxRunningJobsPerUser;
    }

    public void setMaxRunningJobsPerUser(int maxRunningJobsPerUser) {
        this.maxRunningJobsPerUser = maxRunningJobsPerUser;
    }

    @Deprecated
    public boolean isPausingEnabled() {
        return false; //pausingEnabled;
    }

    @Deprecated
    public void setPausingEnabled(boolean pausingEnabled) {
        //this.pausingEnabled = pausingEnabled;
    }

    public boolean isAutodetectMaxConcurrent() {
        return autodetectMaxConcurrent;
    }

    public void setAutodetectMaxConcurrent(boolean autodetectMaxConcurrent) {
        this.autodetectMaxConcurrent = autodetectMaxConcurrent;
        if (autodetectMaxConcurrent) {
            int n = Math.max(Runtime.getRuntime().availableProcessors(), MAX_CONCURRENT_SEARCHES_AT_LEAST);
            logger.debug("maxConcurrentSearches autodetect: setting to " + n);
            maxConcurrentSearches = n;
        }
    }

    @Deprecated
    public int getAbandonedCountPauseTimeSec() {
        return -1; //abandonedCountPauseTimeSec;
    }

    @Deprecated
    public void setAbandonedCountPauseTimeSec(int abandonedCountPauseTimeSec) {
        //this.abandonedCountPauseTimeSec = abandonedCountPauseTimeSec;
    }

    public int getAbandonedCountAbortTimeSec() {
        return abandonedCountAbortTimeSec;
    }

    public void setAbandonedCountAbortTimeSec(int abandonedCountAbortTimeSec) {
        this.abandonedCountAbortTimeSec = abandonedCountAbortTimeSec;
    }

}