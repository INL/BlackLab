package nl.inl.blacklab.searches;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A cache that BlackLab will notify of search results, and will query for
 * previously cached results.
 *
 * It is up to the application to implement the cache, deciding what to cache
 * and ensuring the cache doesn't grow too large.
 *
 * The cache implementation should be made thread-safe.
 */
public interface SearchCache {

    /**
     * Get a [future] result for the specified search.
     *
     * If the search is already in the cache, its existing future will be returned.
     * It may or may not have completed already. If the search is not in the cache, it
     * will be started on a new thread and added to the cache, then its future will be
     * returned.
     *
     * @param <R> type of SearchResult
     * @param search search we want the result for. if the search is not in the cache, execute it,
     *            returning the future result and putting it in the cache.
     * @return the future, either one that was already the cache or a new one
     */
    <R extends SearchResult> Future<R> getAsync(Search<R> search);

    /**
     * Get result for the specified search.
     *
     * Will block until the result is available. If the search was already in the cache,
     * its existing future will be used. If the search is not in the cache, it
     * will be started and added to the cache, then its result will be
     * returned.
     *
     * The default implementation simply calls getAsync() and waits for the future to
     * complete, but other implementations could choose to perform the task on the current thread as well.
     *
     * @param <R> type of SearchResult
     * @param search search we want the result for. if the search is not in the cache, execute it,
     *            returning the future result and putting it in the cache.
     * @return the future, either one that was alrady the cache or a new one
     * @throws InterruptedSearch if the task was interrupted
     * @throws ExecutionException if the task threw an exception (see the cause)
     */
    default <R extends SearchResult> R get(Search<R> search)
            throws ExecutionException {
        try {
            return getAsync(search).get();
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }

    /**
     * Remove a search from the cache.
     *
     * @param <R> type of SearchResult
     * @param search search to remove
     * @return the removed search's future
     */
    <R extends SearchResult> Future<R> remove(Search<R> search);

    /**
     * Remove all searches for a particular index.
     *
     * @param index index to remove searches for
     */
    void removeSearchesForIndex(BlackLabIndex index);

    /**
     * Clear the cache.
     *
     * @param cancelRunning if false: only clear searches that are not running. If true: cancel any running searches and clear them as well.
     */
    void clear(boolean cancelRunning);

    /**
     * Perform any final cleanup.
     */
    void cleanup();

}
