package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchCount extends AbstractSearch {
    
    public SearchCount(QueryInfo queryInfo, List<SearchResultObserver> ops) {
        super(queryInfo, ops);
    }

    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     * @throws InvalidQuery if the query was invalid
     */
    @Override
    public abstract ResultCount execute() throws InvalidQuery;

    /**
     * Report the intermediate result.
     * 
     * @param receiver who to report to
     * @return resulting operation
     */
    @Override
    public abstract SearchCount observe(SearchResultObserver receiver);
    

}
