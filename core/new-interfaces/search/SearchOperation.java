package nl.inl.blacklab.interfaces.search;

import nl.inl.blacklab.interfaces.results.SearchResult;

/**
 * Way to perform custom operations on results from an operation.
 * 
 * This can be useful for e.g. caching an intermediate result.
 */
public interface SearchOperation {
    
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
	SearchResult perform(Search operation, SearchResult result);
}
