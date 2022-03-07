package nl.inl.blacklab.search.results;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import nl.inl.blacklab.exceptions.InterruptedSearch;

/**
 * Returns 0 until it gets the real stats.
 *
 * Useful for peeking at count.
 */
public class ResultsStatsDelegate extends ResultsStats {

    private static final long WAIT_TIME_MS = 50;

    /** Our cache entry */
    private final Future<ResultsStats> future;

    /** The actual stats to monitor, as soon as they're available. Null otherwise. */
    private ResultsStats realStats;

    private CountDownLatch realStatsAvailable;

    public void setRealStats(ResultsStats realStats) {
        this.realStats = realStats;
        realStatsAvailable.countDown();
    }

    public ResultsStatsDelegate(Future<ResultsStats> future) {
        this.future = future;
        realStatsAvailable = new CountDownLatch(1);
    }

    /**
     * Get the running count, or a fake 0 count if not started yet.
     */
    private ResultsStats stats() {
        if (future.isCancelled())
            throw new InterruptedSearch();
        try {
            if (future.isDone())
                return future.get();
        } catch (InterruptedException|ExecutionException e) {
            throw new InterruptedSearch(e);
        }
        // Didn't return in time; results object must not be available yet; return 0
        return realStats == null ? ResultsStats.SEARCH_NOT_STARTED_YET : realStats;
    }

    /**
     * Get the running count, even if we have to wait a while to get it.
     */
    private ResultsStats realStats() {

        try {

            // Wait until real running count is available or the search
            // has been cancelled.
            while (true) {

                // Check that the search hasn't been cancelled
                if (future.isCancelled())
                    throw new InterruptedSearch();

                // We need the actual stats. Wait a short time for them,
                // or until the underlying search is cancelled.
                if (realStatsAvailable.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS)) {
                    return realStats;
                }
            }
        } catch (InterruptedException e) {
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
