package nl.inl.blacklab.server.config;

public class BLSConfigCache {

    @Deprecated
    int maxSizeMegs = 100;

    @Deprecated
    int maxNumberOfJobs = 100;

    int maxSearchTimeSec = 300;

    int maxJobAgeSec = 3600;

    int targetFreeMemMegs = 100;

    int minFreeMemForSearchMegs = 50;

    int clientCacheTimeSec = 3600;

    int maxQueuedSearches = 20;

    private int denyAbortedSearchSec = 600;

    @Deprecated
    public int getMaxSizeMegs() {
        return maxSizeMegs;
    }

    @Deprecated
    public void setMaxSizeMegs(int maxSizeMegs) {
        this.maxSizeMegs = maxSizeMegs;
    }

    @Deprecated
    public int getMaxNumberOfJobs() {
        return maxNumberOfJobs;
    }

    @Deprecated
    public void setMaxNumberOfJobs(int maxNumberOfJobs) {
        this.maxNumberOfJobs = maxNumberOfJobs;
    }

    public int getMaxJobAgeSec() {
        return maxJobAgeSec;
    }

    public void setMaxJobAgeSec(int maxJobAgeSec) {
        this.maxJobAgeSec = maxJobAgeSec;
    }

    public int getTargetFreeMemMegs() {
        return targetFreeMemMegs;
    }

    public void setTargetFreeMemMegs(int targetFreeMemMegs) {
        this.targetFreeMemMegs = targetFreeMemMegs;
    }

    public int getMinFreeMemForSearchMegs() {
        return minFreeMemForSearchMegs;
    }

    public void setMinFreeMemForSearchMegs(int minFreeMemForSearchMegs) {
        this.minFreeMemForSearchMegs = minFreeMemForSearchMegs;
    }

    public int getClientCacheTimeSec() {
        return clientCacheTimeSec;
    }

    public void setClientCacheTimeSec(int clientCacheTimeSec) {
        this.clientCacheTimeSec = clientCacheTimeSec;
    }

    public int getMaxSearchTimeSec() {
        return maxSearchTimeSec;
    }

    public void setMaxSearchTimeSec(int maxSearchTimeSec) {
        this.maxSearchTimeSec = maxSearchTimeSec;
    }

    public int getMaxQueuedSearches() {
        return maxQueuedSearches;
    }

    public void setMaxQueuedSearches(int maxQueuedSearches) {
        this.maxQueuedSearches = maxQueuedSearches;
    }

    public int getDenyAbortedSearchSec() {
        return denyAbortedSearchSec;
    }

    public void setDenyAbortedSearchSec(int denyAbortedSearchSec) {
        this.denyAbortedSearchSec = denyAbortedSearchSec;
    }

}