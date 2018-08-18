package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * A cache that BlackLab will notify of search results,
 * and will query for previously cached results.
 * 
 * It is up to the application to implement the cache, deciding
 * what to cache and ensuring the cache doesn't grow too large.
 */
public interface SearchCache {
    
    /**
     * A search was executed, and this was the result.
     * 
     * The cache may decide to cache this result.
     * 
     * @param search search operation that produced this result
     * @param result the result
     */
    void onSearchResult(Search search, SearchResult result);

    /**
     * See if the specified search was previously cached.
     * 
     * @param search search we want the result for
     * @return the result if it was in the cache, or null otherwise
     */
    SearchResult get(Search search);
    
}
