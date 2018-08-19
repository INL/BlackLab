package nl.inl.blacklab.searches;

import java.util.concurrent.CompletableFuture;
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
     * @param searchTask if not found, put a future using this supplier in the cache and return it
     * @return the future, either one that was alrady the cache or a new one using the supplier
     */
    CompletableFuture<? extends SearchResult> get(Search search, Supplier<? extends SearchResult> searchTask);
    
    void remove(Search search);
    
}
