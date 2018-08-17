package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * Way to perform custom operations on results from an operation.
 * 
 * This can be useful for e.g. caching an intermediate result.
 */
public interface SearchOperation {
    
    /**
     * Perform the operation on the result.
     * 
     * @param operation search operation that produced this result
     * @param result the result
     */
    void perform(Search operation, SearchResult result);
}
