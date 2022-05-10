package nl.inl.blacklab.search.results;

/**
 * Static implementation of ResultsStats, suitable for
 * when a search has ended and we want to ensure we don't
 * keep a reference to the search.
 */
public class ResultsStatsStatic extends ResultsStats {

    /** Used to avoid NPE when ResultsStats is not available for whatever reason. */
    public static final ResultsStats INVALID = new ResultsStatsStatic(-1, -1, new MaxStats(true, true));

    private final long processed;

    private final long counted;

    private final MaxStats maxStats;

    public ResultsStatsStatic(long processed, long counted, MaxStats maxStats) {
        this.processed = processed;
        this.counted = counted;
        this.maxStats = maxStats;
    }

    @Override
    public long processedTotal() {
        return processed;
    }

    @Override
    public long countedTotal() {
        return counted;
    }

    @Override
    public boolean processedAtLeast(long lowerBound) {
        return processedSoFar() >= lowerBound;
    }

    @Override
    public long processedSoFar() {
        return processedTotal();
    }

    @Override
    public long countedSoFar() {
        return countedTotal();
    }

    @Override
    public boolean done() {
        return true;
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public MaxStats maxStats() {
        return maxStats;
    }

    @Override
    public String toString() {
        return "ResultsStatsStatic [processed=" + processed + ", counted=" + counted + ", maxStats=" + maxStats + "]";
    }

}
