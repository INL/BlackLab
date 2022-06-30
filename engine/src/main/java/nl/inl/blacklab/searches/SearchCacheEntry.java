package nl.inl.blacklab.searches;

import java.util.concurrent.Future;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * An entry in BlackLab's search cache.
 *
 * Custom implementations of SearchCache can subclass this to implement custom logic.
 *
 * @param <R> the type of search result this search will yield
 */
public abstract class SearchCacheEntry<R extends SearchResult> implements Future<R>, SearchTask<R> {

    /** When was our processing timer started? */
    private long timerStart = -1;

    /** Total processing time for this task (ms), including that of subtasks. */
    private long processingTime = 0;

    /** Get the total processing time for this task (ms).
     *
     * This includes processing time for other tasks it used (e.g. a "sorted hits" task calculates
     * its processing time by adding the time it took to retrieve all the hits and the time it took
     * to sort them, even though the task itself only does the actual sorting).
     *
     * Processing time is intended to be independent from the cache: it keeps track only of the actual
     * time processing (originally) took. So even if a request is almost instant, processing time can
     * be much higher if the original search took that long.
     */
    public long processingTimeMs() {
        return processingTime;
    }

    /** (Re)start the task's processing timer, adding to its total. */
    public void startTimer() {
        timerStart = System.currentTimeMillis();
    }

    /** Stop the task's processing timer, (temporarily) not keeping track of time elapsed. */
    public void stopTimer() {
        processingTime += System.currentTimeMillis() - timerStart;
    }

    /** Add the processing time for the subtask to this tasks's processing time. */
    public void addSubtaskTime(SearchTask<?> subtask) {
        processingTime += subtask.processingTimeMs();
    }

    /**
     * Has this search been started?
     *
     * @return true if it was, false if not
     */
    public abstract boolean wasStarted();

    /**
     * Subclasses should implement this to start the search.
     *
     * Search entries initially haven't been started. We call unstarted searches 'queued',
     * because they are waiting for enough resources to be available to be started.
     * When there are, this method is called to start the search.
     */
    public abstract void start();

    static <S extends SearchResult> SearchCacheEntry<S> fromFuture(Future<S> future, Search<S> search) {
        return new SearchCacheEntryFromFuture<>(future, search);
    }

    /**
     * Is this search currently running?
     * @return true if the search is running (not queued, cancelled or completed)
     */
    public boolean isRunning() {
        return wasStarted() && !isDone();
    }

    /**
     * If this search couldn't complete or was aborted, what it the reason?
     *
     * @return descriptive reason or empty string
     */
    public String getReason() {
        return "";
    }

    public abstract long timeUserWaitedMs();

    public boolean threwException() {
        return false;
    }

    /**
     * Peek at the result even if it's not yet finished.
     *
     * Used for running counts.
     *
     * @return the result so far, or null if not supported for this operation
     */
    public R peek() {
        if (isCancelled())
            throw InterruptedSearch.cancelled();
        return null;
    }

}
