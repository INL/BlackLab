package nl.inl.blacklab.searches;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * A cache that BlackLab will notify of search results,
 * and will query for previously cached results.
 * 
 * It is up to the application to implement the cache, deciding
 * what to cache and ensuring the cache doesn't grow too large.
 * 
 * The cache implementation should be made thread-safe.
 */
public interface SearchCache {

    /**
     * See if the specified search was previously cached.
     * 
     * @param search search we want the result for
     * @param searchTask if the search is not in the cache, execute this task, returning the future result and putting it in the cache
     * @return the future, either one that was alrady the cache or a new one using the supplier
     */
    Future<? extends SearchResult> get(Search search, Supplier<? extends SearchResult> searchTask);
    
    Future<? extends SearchResult> remove(Search search);
    
}
