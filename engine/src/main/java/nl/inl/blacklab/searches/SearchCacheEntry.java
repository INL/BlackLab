package nl.inl.blacklab.searches;

import java.util.concurrent.Future;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * An entry in BlackLab's search cache.
 *
 * Custom implementations of SearchCache can subclass this to implement custom logic.
 *
 * @param <R> the type of search result this search will yield
 */
public abstract class SearchCacheEntry<R extends SearchResult> implements Future<R> {

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

    static <S extends SearchResult> SearchCacheEntry<S> fromFuture(Future<S> future) {
        return new SearchCacheEntryFromFuture<S>(future);
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
}
