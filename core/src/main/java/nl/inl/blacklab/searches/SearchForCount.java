package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.ResultCount;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchForCount extends AbstractSearch<ResultCount> {
    
    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     */
    @Override
    public abstract ResultCount execute();

    /**
     * Report the intermediate result.
     * 
     * @param receiver who to report to
     * @return resulting operation
     */
    @Override
    public abstract SearchForCount custom(SearchOperation<ResultCount> receiver);
    

}
