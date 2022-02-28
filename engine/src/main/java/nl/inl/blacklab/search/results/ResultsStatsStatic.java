package nl.inl.blacklab.search.results;

/**
 * Static implementation of ResultsStats, suitable for
 * when a search has ended and we want to ensure we don't
 * keep a reference to the search.
 */
public class ResultsStatsStatic extends ResultsStats {

    private long processed;

    private long counted;

    private MaxStats maxStats;

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
    public boolean processedAtLeast(int lowerBound) {
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
