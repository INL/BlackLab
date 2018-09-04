package nl.inl.blacklab.server.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BLSConfigPerformance {
    
    private static final Logger logger = LogManager.getLogger(BLSConfigPerformance.class);
    
    boolean autodetectMaxConcurrent = false;
    
    int maxConcurrentSearches = 4;
    
    int maxThreadsPerSearch = 2;
    
    boolean pausingEnabled = false;
    
    int maxPausedSearches = 6;
    
    int abandonedCountPauseTimeSec = 10;
    
    int abandonedCountAbortTimeSec = 30;
    
    int maxRunningJobsPerUser = 10;

    public int getMaxConcurrentSearches() {
        return maxConcurrentSearches;
    }

    public void setMaxConcurrentSearches(int maxConcurrentSearches) {
        this.maxConcurrentSearches = maxConcurrentSearches;
        if (maxConcurrentSearches <= 0) {
            int n = Math.max(Runtime.getRuntime().availableProcessors(), 1);
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

    public int getMaxPausedSearches() {
        return maxPausedSearches;
    }

    public void setMaxPausedSearches(int maxPausedSearches) {
        this.maxPausedSearches = maxPausedSearches;
    }

    public int getMaxRunningJobsPerUser() {
        return maxRunningJobsPerUser;
    }

    public void setMaxRunningJobsPerUser(int maxRunningJobsPerUser) {
        this.maxRunningJobsPerUser = maxRunningJobsPerUser;
    }

    public boolean isPausingEnabled() {
        return pausingEnabled;
    }

    public void setPausingEnabled(boolean pausingEnabled) {
        this.pausingEnabled = pausingEnabled;
    }

    public boolean isAutodetectMaxConcurrent() {
        return autodetectMaxConcurrent;
    }

    public void setAutodetectMaxConcurrent(boolean autodetectMaxConcurrent) {
        this.autodetectMaxConcurrent = autodetectMaxConcurrent;
        if (autodetectMaxConcurrent) {
            int n = Math.max(Runtime.getRuntime().availableProcessors(), 1);
            logger.debug("maxConcurrentSearches autodetect: setting to " + n);
            maxConcurrentSearches = n;
        }
    }

    public int getAbandonedCountPauseTimeSec() {
        return abandonedCountPauseTimeSec;
    }

    public void setAbandonedCountPauseTimeSec(int abandonedCountPauseTimeSec) {
        this.abandonedCountPauseTimeSec = abandonedCountPauseTimeSec;
    }

    public int getAbandonedCountAbortTimeSec() {
        return abandonedCountAbortTimeSec;
    }

    public void setAbandonedCountAbortTimeSec(int abandonedCountAbortTimeSec) {
        this.abandonedCountAbortTimeSec = abandonedCountAbortTimeSec;
    }
    
}