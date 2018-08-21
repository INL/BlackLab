package nl.inl.blacklab.search.results;

/** How many hits we want to process/count at most. */
public final class MaxSettings {
    
    public static MaxSettings defaults() {
        return new MaxSettings();
    }

    /** When setting how many hits to retrieve/count, this means "no limit". */
    public static final int NO_LIMIT = -1;
    
    /** How many hits to process by default */
    public static final int DEFAULT_MAX_PROCESS = 1000000;
    
    /** How many hits to count by default */
    public static final int DEFAULT_MAX_COUNT = NO_LIMIT;
    
    /**
     * Stop retrieving hits after this number. (NO_LIMIT = -1 = don't stop
     * retrieving)
     */
    private int maxHitsToProcess;

    /**
     * Stop counting hits after this number. (NO_LIMIT = -1 = don't stop counting)
     */
    private int maxHitsToCount;

    /**
     * Get an instance.
     * @param maxHitsToProcess how many hits to process at most
     * @param maxHitsToCount how many hits to count at most
     */
    public MaxSettings(int maxHitsToProcess, int maxHitsToCount) {
        this.maxHitsToProcess = maxHitsToProcess;
        this.maxHitsToCount = maxHitsToCount;
    }

    /**
     * Get default hits settings for an index
     * @param index index
     */
    private MaxSettings() {
        maxHitsToProcess = DEFAULT_MAX_PROCESS;
        maxHitsToCount = DEFAULT_MAX_COUNT;
    }

    /** @return the maximum number of hits to retrieve. */
    public int maxHitsToProcess() {
        return maxHitsToProcess;
    }

    /** @return the maximum number of hits to count. */
    public int maxHitsToCount() {
        return maxHitsToCount;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
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
        MaxSettings other = (MaxSettings) obj;
        if (maxHitsToCount != other.maxHitsToCount)
            return false;
        return maxHitsToProcess == other.maxHitsToProcess;
    }
    
    @Override
    public String toString() {
        return "MaxSettings(" + maxHitsToProcess + ", " + maxHitsToCount + ")";
    }

}
