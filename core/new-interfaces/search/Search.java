package nl.inl.blacklab.interfaces.search;

import nl.inl.blacklab.interfaces.results.SearchResult;

/**
 * A 'recipe' of search operations.
 */
public interface Search {

    /** Execute the search operation, returning the final response. 
     * @return result of the operation */
    SearchResult execute();

    /**
     * Report the intermediate result.
     * 
     * @param receiver who to report to
     * @return resulting operation
     */
    Search custom(SearchOperation receiver);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

}