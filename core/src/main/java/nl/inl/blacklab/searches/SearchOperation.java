package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * Way to perform custom operations on results from an operation.
 * 
 * This can be useful for e.g. caching an intermediate result.
 * 
 * @param <T> type of SearchResult we operate on, e.g. Hits
 */
public interface SearchOperation<T extends SearchResult> {
    
    /**
     * Perform the operation on the result.
     * 
     * If you use this for caching, make sure you call .save() on the
     * result to ensure immutability, and return that immutable instance. 
     * 
     * @param operation search operation that produced this result
     * @param result the result
     * @return processed result
     */
	T perform(Search<T> operation, T result);
}
