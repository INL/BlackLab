package nl.inl.blacklab.searches;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Supplier;

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
     * @return the future, either one that was alrady the cache or a new one using
     *         the supplier
     * @throws InterruptedException 
     */
    Future<? extends SearchResult> getAsync(Search search, Supplier<? extends SearchResult> searchTask) throws InterruptedException;

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
     * @throws InterruptedException if the task was interrupted
     * @throws ExecutionException if the task threw an exception (see the cause)
     */
    default SearchResult get(Search search, Supplier<? extends SearchResult> searchTask)
            throws InterruptedException, ExecutionException {
        return getAsync(search, searchTask).get();
    }
    
    Future<? extends SearchResult> remove(Search search);

}
