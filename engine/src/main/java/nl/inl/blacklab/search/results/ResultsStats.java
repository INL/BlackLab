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
        return new ResultsStatsStatic(processedSoFar(), countedSoFar(), maxStats());
    }

    /**
     * Is this a static count?
     *
     * @return true if this is a static (saved) count, false if it is dynamically linked to a search
     */
    public boolean isStatic() {
        return false;
    }

    /**
     * Get information about exceeding maximums.
     *
     * @return max stats
     */
    public abstract MaxStats maxStats();

    /**
     * Was this count interrupted?
     *
     * This can happen if you implement a system that aborts long-running or memory-hungry searches.
     * If so, the total counts may not reflect reality.
     *
     * @return true if the count was interrupted, false if not
     */
    public boolean wasInterrupted() {
        return false;
    }

    @Override
    public abstract String toString();

}
