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
     * Count hits. All results are fetched in the cache thread.
     * 
     * @return resulting operation
     */
    public SearchCount count() {
        return count(true);
    }

    /**
     * Count hits.
     * 
     * @param fetchAllResults should all results be fetched in the cache thread..?
     * @return resulting operation
     */
    public SearchCount count(boolean fetchAllResults) {
        return new SearchCountFromResults<R>(queryInfo(), this, fetchAllResults);
    }
    
    @Override
    public abstract boolean equals(Object obj);
    
    @Override
    public abstract int hashCode();
    
}
