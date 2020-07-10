package nl.inl.blacklab.searches;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

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
     * @param search search we want the result for
     * @param searchTask if the search is not in the cache, execute this task,
     *            returning the future result and putting it in the cache
     * @param fetchAllResults if true, and search yield a Results object, will fetch all results before the thread ends
     *                        this can be used for running total counts, for example
     * @return the future, either one that was alrady the cache or a new one using
     *         the supplier
     */
    <R extends SearchResult> Future<R> getAsync(Search<R> search, Supplier<R> searchTask);

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
     * @param search search we want the result for
     * @param searchTask if the search is not in the cache, execute this task,
     *            returning the future result and putting it in the cache
     * @return the future, either one that was alrady the cache or a new one using
     *         the supplier
     * @throws InterruptedSearch if the task was interrupted
     * @throws ExecutionException if the task threw an exception (see the cause)
     */
    default <R extends SearchResult> R get(Search<R> search, Supplier<R> searchTask)
            throws ExecutionException {
        try {
            return getAsync(search, searchTask).get();
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }
    
    <R extends SearchResult> Future<R> remove(Search<R> search);

    void removeSearchesForIndex(BlackLabIndex index);

    void clear(boolean b);

    void cleanup();

}
