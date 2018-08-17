package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * A 'recipe' of search operations.
 * 
 * @param <T> type of SearchResult this search will yield, e.g. Hits
 */
public interface Search<T extends SearchResult> {

    /** Execute the search operation, returning the final response. 
     * @return result of the operation */
    T execute();

    /**
     * Report the intermediate result.
     * 
     * @param receiver who to report to
     * @return resulting operation
     */
    Search<T> custom(SearchOperation<T> receiver);

    @Override
    boolean equals(Object obj);

    @Override
    int hashCode();

}