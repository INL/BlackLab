package nl.inl.blacklab.searches;

import java.util.concurrent.Future;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A 'recipe' of search operations.
 */
public interface Search {

    /**
     * Execute the search operation, returning the final response.
     * 
     * Executes the search synchronously.
     *  
     * @return result of the operation 
     * @throws InvalidQuery
     * @throws InterruptedException 
     */
    SearchResult execute() throws InvalidQuery, InterruptedException;

    /**
     * Execute the search operation asynchronously.
     * 
     * Runs the search in a separate thread and passes the result through
     * the returned Future.
     * 
     * @return future result
     * @throws InterruptedException 
     */
    Future<? extends SearchResult> executeAsync() throws InterruptedException;
    
    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    QueryInfo queryInfo();
    
    @Override
    String toString();

}