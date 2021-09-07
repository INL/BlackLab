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
     * Is this search queued?
     * @return true if it is, false if not
     */
    public abstract boolean isQueued();

    /**
     * If this search is queued, start it now.
     *
     * If a Search requires the result of another Search, it should call this method
     * to make sure no deadlock occurs.
     *
     * The load management system will also start queued searches if server load
     * is low enough.
     */
    void startIfQueued() {
        if (isQueued())
            startQueuedSearchImpl();
    }

    /**
     * Subclasses should implement this to start the queued search.
     */
    public abstract void startQueuedSearchImpl();

    static <S extends SearchResult> SearchCacheEntry<S> fromFuture(Future<S> future) {
        return new SearchCacheEntryFromFuture<S>(future);
    }
}
