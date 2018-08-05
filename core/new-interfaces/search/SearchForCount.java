package nl.inl.blacklab.interfaces.search;

import nl.inl.blacklab.interfaces.results.CountResult;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchForCount extends AbstractSearch {
    
    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     */
    @Override
    public abstract CountResult execute();

    /**
     * Report the intermediate result.
     * 
     * @param receiver who to report to
     * @return resulting operation
     */
    @Override
    public abstract SearchForCount custom(SearchOperation receiver);
    

}
