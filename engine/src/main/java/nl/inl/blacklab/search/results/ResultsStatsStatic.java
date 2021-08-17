package nl.inl.blacklab.search.results;

/**
 * Static implementation of ResultsStats, suitable for
 * when a search has ended and we want to ensure we don't
 * keep a reference to the search.
 */
public class ResultsStatsStatic extends ResultsStats {

    private int processed;

    private int counted;

    private MaxStats maxStats;

    public ResultsStatsStatic(int processed, int counted, MaxStats maxStats) {
        this.processed = processed;
        this.counted = counted;
        this.maxStats = maxStats;
    }

    @Override
    public int processedTotal() {
        return processed;
    }

    @Override
    public int countedTotal() {
        return counted;
    }

    @Override
    public boolean processedAtLeast(int lowerBound) {
        return processedSoFar() >= lowerBound;
    }

    @Override
    public int processedSoFar() {
        return processedTotal();
    }

    @Override
    public int countedSoFar() {
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
