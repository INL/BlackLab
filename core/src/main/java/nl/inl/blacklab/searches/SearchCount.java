package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchCount extends AbstractSearch<ResultCount> {
    
    public SearchCount(QueryInfo queryInfo) {
        super(queryInfo);
    }

    
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
