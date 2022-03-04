package nl.inl.blacklab.search.results;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import nl.inl.blacklab.exceptions.InterruptedSearch;

/**
 * Returns 0 until it gets the real stats.
 *
 * Useful for peeking at count.
 */
public class ResultsStatsDelegate extends ResultsStats {

    private static final long MAX_SEARCH_WAIT_TIME_MS = 20;

    /** Our cache entry */
    private final Future<ResultsStats> future;

    public ResultsStatsDelegate(Future<ResultsStats> future) {
        this.future = future;
    }

    private ResultsStats stats() {
        if (future.isCancelled())
            throw new InterruptedSearch();
        try {
            // Wait a short time in case the underlying search was already done,
            // but that needs to be established for this Future to complete.
            // (e.g. a count for an already-completed hits or docs result still
            //  needs to check that its result object was indeed completed)
            return future.get(MAX_SEARCH_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException|ExecutionException e) {
            throw new InterruptedSearch(e);
        } catch (TimeoutException e) {
            // Didn't return in time; results object must not be available yet; return 0
            return ResultsStats.SEARCH_NOT_STARTED_YET;
        }
    }

    private ResultsStats realStats() {
        if (future.isCancelled())
            throw new InterruptedSearch();
        try {
            // We need the actual stats. Wait for them, or until the underlying search is cancelled.
            return future.get();
        } catch (InterruptedException|ExecutionException e) {
            throw new InterruptedSearch(e);
        }
    }

    @Override
    public int processedTotal() {
        return realStats().processedTotal();
    }

    @Override
    public int countedTotal() {
        return realStats().countedTotal();
    }

    @Override
    public boolean processedAtLeast(int lowerBound) {
        return stats().processedAtLeast(lowerBound);
    }

    @Override
    public int processedSoFar() {
        return stats().processedSoFar();
    }

    @Override
    public int countedSoFar() {
        return stats().countedSoFar();
    }

    @Override
    public boolean done() {
        return stats().done();
    }

    @Override
    public boolean isStatic() {
        if (future.isCancelled())
            throw new InterruptedSearch();
        if (future.isDone())
            return realStats().isStatic();
        return false;
    }

    @Override
    public MaxStats maxStats() {
        return stats().maxStats();
    }

    @Override
    public String toString() {
        return "ResultsStatsDelegate{cacheEntry=" + future + "}";
    }
}
