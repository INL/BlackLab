package nl.inl.blacklab.search.results;

/** Settings for our initial search, including how many hits we want to process/count at most. */
public final class SearchSettings {
    
    public static SearchSettings get(int maxHitsToProcess, int maxHitsToCount, long fiMatchFactor) {
        return new SearchSettings(maxHitsToProcess, maxHitsToCount, fiMatchFactor);
    }

    public static SearchSettings get(int maxHitsToProcess, int maxHitsToCount) {
        return new SearchSettings(maxHitsToProcess, maxHitsToCount, -1);
    }

    public static SearchSettings defaults() {
        return new SearchSettings(DEFAULT_MAX_PROCESS, DEFAULT_MAX_COUNT, -1);
    }
    
    /** How many hits to process by default */
    public static final int DEFAULT_MAX_PROCESS = 10_000_000;
    
    /** How many hits to count by default */
    public static final int DEFAULT_MAX_COUNT = Results.NO_LIMIT;
    
    /**
     * Stop processing hits after this number. (Results.NO_LIMIT = don't stop
     * processing)
     * 
     * Even if we stop processing, we can still keep counting.
     */
    private int maxHitsToProcess;

    /**
     * Stop counting hits after this number. (ResultsNO_LIMIT = don't stop counting)
     */
    private int maxHitsToCount;
    
    /** Override FI match NFA factor, or -1 for default */
    private long fiMatchFactor;

    /**
     * Get settings
     * @param maxHitsToProcess how many hits to process at most
     * @param maxHitsToCount how many hits to count at most
     */
    private SearchSettings(int maxHitsToProcess, int maxHitsToCount, long fiMatchFactor) {
        this.maxHitsToProcess = maxHitsToProcess;
        this.maxHitsToCount = maxHitsToCount;
        this.fiMatchFactor = fiMatchFactor;
    }
    
    public SearchSettings withMaxHitsToProcess(int n) {
        return get(n, maxHitsToCount, fiMatchFactor);
    }

    public SearchSettings withMaxHitsToCount(int n) {
        return get(maxHitsToProcess, n, fiMatchFactor);
    }

    public SearchSettings withFiMatchFactor(int n) {
        return get(maxHitsToProcess, maxHitsToCount, n);
    }

    /** @return the maximum number of hits to retrieve. */
    public int maxHitsToProcess() {
        return maxHitsToProcess;
    }

    /** @return the maximum number of hits to count. */
    public int maxHitsToCount() {
        return maxHitsToCount;
    }

    public long fiMatchFactor() {
        return fiMatchFactor;
    }

    @Override
    public String toString() {
        return "SearchSettings(" + maxHitsToProcess + ", " + maxHitsToCount + ", " + fiMatchFactor + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int)fiMatchFactor;
        result = prime * result + maxHitsToCount;
        result = prime * result + maxHitsToProcess;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchSettings other = (SearchSettings) obj;
        if (fiMatchFactor != other.fiMatchFactor)
            return false;
        if (maxHitsToCount != other.maxHitsToCount)
            return false;
        if (maxHitsToProcess != other.maxHitsToProcess)
            return false;
        return true;
    }
    
    

}
