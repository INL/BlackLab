package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchCount extends AbstractSearch {
    
    public SearchCount(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     * @throws InvalidQuery if the query was invalid
     */
    @Override
    public final ResultCount execute() throws InvalidQuery {
        ResultCount result = (ResultCount)getFromCache(this);
        if (result != null)
            return result;
        return notifyCache(executeInternal());
    }
    
    protected abstract ResultCount executeInternal() throws InvalidQuery;

    
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
