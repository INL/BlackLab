package nl.inl.blacklab.search.results;

public final class HitsSettings {
    
    public static HitsSettings defaults() {
        return new HitsSettings();
    }

    /** When setting how many hits to retrieve/count, this means "no limit". */
    private static final int UNLIMITED_HITS = -1;
    
    public static final int DEFAULT_MAX_RETRIEVE = 1000000;
    
    public static final int DEFAULT_MAX_COUNT = UNLIMITED_HITS;
    
    /** Annotated field name for default contents field */
    public static final String DEFAULT_CONTENTS_FIELD_NAME = "contents";
    
    public static final int DEFAULT_CONTEXT_SIZE = 5;

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
     * Get a (non-frozen) copy of a HitsSettings instance.
     * 
     * @param copyFrom instance to copy
     */
    private HitsSettings(HitsSettings copyFrom) {
        maxHitsToProcess = copyFrom.maxHitsToProcess();
        maxHitsToCount = copyFrom.maxHitsToCount();
    }

    /**
     * Get default hits settings for an index
     * @param index index
     */
    private HitsSettings() {
        maxHitsToProcess = DEFAULT_MAX_RETRIEVE;
        maxHitsToCount = DEFAULT_MAX_COUNT;
    }
    
    private HitsSettings copy() {
        return new HitsSettings(this);
    }

    /** @return the maximum number of hits to retrieve. */
    public int maxHitsToProcess() {
        return maxHitsToProcess;
    }

    /** @return the maximum number of hits to count. */
    public int maxHitsToCount() {
        return maxHitsToCount;
    }
    
    
    // Methods that mutate data
    //------------------------------------------------------------------------

    /**
     * Get settings with different maximum number of hits to retrieve
     * 
     * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
     * @return settings object
     */
    public HitsSettings withMaxHitsToRetrieve(int n) {
        HitsSettings x = copy();
        x.maxHitsToProcess = n;
        return x;
    }

    /**
     * Get settings with different maximum number of hits to count
     * 
     * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
     * @return settings object
     */
    public HitsSettings withMaxHitsToCount(int n) {
        HitsSettings x = copy();
        x.maxHitsToCount = n;
        return x;
    }

}
