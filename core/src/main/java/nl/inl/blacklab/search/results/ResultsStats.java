package nl.inl.blacklab.search.results;

public abstract class ResultsStats {
    
    public abstract boolean processedAtLeast(int lowerBound);

    /**
     * This is an alias of resultsProcessedTotal().
     * 
     * @return number of hits processed total
     */
    public int size() {
        return processedTotal();
    }

    public abstract int processedTotal();

    public abstract int processedSoFar();

    public abstract int countedSoFar();
    
    public abstract int countedTotal();
    
    public abstract boolean done();

    /**
     * Save the current counts to a static object.
     * 
     * The resulting object doesn't hold a reference to the search anymore.
     * 
     * It only saves the results processed and counted so far, and considers those
     * the totals.
     * 
     * @return static instance of current stats
     */
    public ResultsStats save() {
        return new ResultsStatsStatic(processedSoFar(), countedSoFar());
    }

    /**
     * Is this a static count?
     * 
     * @return true if this is a static (saved) count, false if it is dynamically linked to a search
     */
    public boolean isStatic() {
        return false;
    }
    
}
