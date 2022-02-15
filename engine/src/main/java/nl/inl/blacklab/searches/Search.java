package nl.inl.blacklab.searches;

import java.util.Collection;
import java.util.Collections;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A 'recipe' of search operations.
 *
 * @param <R> results type, e.g. Hits
 */
public interface Search<R extends SearchResult> {

    /**
     * Execute the search operation, returning the final response.
     *
     * Executes the search synchronously.
     *
     * This search may be queued (i.e. kept from starting until later) if load is high. To avoid this (and potential
     * deadlock situations because of it), use {@link #executeNoQueue()} or {@link #execute(boolean)}.
     *
     * @return result of the operation
     * @throws InvalidQuery
     */
    default R execute() throws InvalidQuery {
        return execute(true);
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * Executes the search synchronously.
     *
     * This search will be started right away, even if load is high.
     * For more control, use {@link #execute(boolean)}.
     *
     * @return result of the operation
     * @throws InvalidQuery
     */
    default R executeNoQueue() throws InvalidQuery {
        return execute(false);
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * Executes the search synchronously.
     *
     * @param allowQueue may this search be queued (i.e. kept from starting until later)
     * if load is high? Set to false (for searches initiated by other searches)
     * to prevent deadlock,
     * or true (for user-initiated searches) to allow load management to happen.
     * @return result of the operation
     * @throws InvalidQuery
     */
    R execute(boolean allowQueue) throws InvalidQuery;

    /**
     * Execute the search operation asynchronously.
     *
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     *
     * This search may be queued (i.e. kept from starting until later) if load is high.
     * To avoid this (and potential deadlock situations because of it), use
     * {@link #executeAsyncNoQueue()} or {@link #executeAsync(boolean)}.
     *
     * @return future result
     */
    default SearchCacheEntry<R> executeAsync() {
        return executeAsync(true);
    }

    /**
     * Execute the search operation asynchronously.
     *
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     *
     * This search will be started right away, even if load is high.
     * For more control, use {@link #executeAsync(boolean)}.
     *
     * @return future result
     */
    default SearchCacheEntry<R> executeAsyncNoQueue() {
        return executeAsync(false);
    }

    /**
     * Execute the search operation asynchronously.
     *
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     *
     * @param allowQueue may this search be queued (i.e. kept from starting until later)
     * if load is high? Set to false (for searches initiated by other searches) to
     * prevent deadlock,
     * or true (for user-initiated searches) to allow load management to happen.
     *
     * @return future result
     */
    SearchCacheEntry<R> executeAsync(boolean allowQueue);

    /**
     * Actually executes this search operation.
     *
     * This is where subclasses should implement the actual search operation.
     *
     * executeAsync submits this search to the cache, which then calls this method from a Runnable
     * to run the search.
     *
     * @return result of the operation
     * @throws InvalidQuery
     */
    R executeInternal() throws InvalidQuery;

    /**
     * Return other cache entries we need to perform our task.
     *
     * If these are aborted, we will also be aborted. If we're used by a request,
     * these cache entries should also be pinged so they're kept alive.
     *
     * For example: a SearchCount ("report on running total") needs its
     * SearchCountTotal ("gather all results until total is known") to keep running.
     *
     * Note that this method will be called once after executeInternal runs,
     * and will not be called again.
     *
     * @return other cache entries we need to function
     */
    default Collection<SearchCacheEntry> getRequiredOtherCacheEntries() {
        return Collections.emptyList();
    }

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    QueryInfo queryInfo();

    @Override
    String toString();

}