package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.Results;

/** A search that yields results. 
 * @param <R> results type, e.g. Hits
 */
public abstract class SearchResults<R extends Results<?>> extends AbstractSearch<R> {

    SearchResults(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Count hits.
     * @return resulting operation
     */
    public SearchCount count() {
        return new SearchCountFromResults<R>(queryInfo(), this);
    }
    
    @Override
    public abstract boolean equals(Object obj);
    
    @Override
    public abstract int hashCode();
    
}
