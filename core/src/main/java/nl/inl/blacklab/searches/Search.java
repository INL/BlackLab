package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A 'recipe' of search operations.
 */
public interface Search {

    /** Execute the search operation, returning the final response. 
     * @return result of the operation 
     * @throws InvalidQuery */
    SearchResult execute() throws InvalidQuery;
    
    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

    QueryInfo queryInfo();

}