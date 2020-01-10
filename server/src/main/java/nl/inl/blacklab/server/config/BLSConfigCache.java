package nl.inl.blacklab.server.config;

public class BLSConfigCache {
    
    int maxSizeMegs = 100;
    
    int maxNumberOfJobs = 100;
    
    int maxSearchTimeSec = 300;
    
    int maxJobAgeSec = 3600;
    
    int targetFreeMemMegs = 100;
    
    int minFreeMemForSearchMegs = 50;
    
    int clientCacheTimeSec = 3600;

    public int getMaxSizeMegs() {
        return maxSizeMegs;
    }

    public void setMaxSizeMegs(int maxSizeMegs) {
        this.maxSizeMegs = maxSizeMegs;
    }

    public int getMaxNumberOfJobs() {
        return maxNumberOfJobs;
    }

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
}