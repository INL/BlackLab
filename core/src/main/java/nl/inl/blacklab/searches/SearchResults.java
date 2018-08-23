package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount.CountType;
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
     * Count hits. All results are fetched in the cache thread.
     * 
     * @return resulting operation
     */
    public SearchCount hitCount() {
        return hitCount(true);
    }

    /**
     * Count docs. All results are fetched in the cache thread.
     * 
     * @return resulting operation
     */
    public SearchCount docCount() {
        return docCount(true);
    }

    /**
     * Count results.
     * 
     * @param fetchAllResults should all results be fetched in the cache thread..?
     * @return resulting operation
     */
    public SearchCount count(boolean fetchAllResults) {
        return new SearchCountFromResults<>(queryInfo(), this, CountType.RESULTS, fetchAllResults);
    }

    /**
     * Count hits.
     * 
     * @param fetchAllResults should all results be fetched in the cache thread..?
     * @return resulting operation
     */
    public SearchCount hitCount(boolean fetchAllResults) {
        return new SearchCountFromResults<>(queryInfo(), this, CountType.HITS, fetchAllResults);
    }

    /**
     * Count docs.
     * 
     * @param fetchAllResults should all results be fetched in the cache thread..?
     * @return resulting operation
     */
    public SearchCount docCount(boolean fetchAllResults) {
        return new SearchCountFromResults<>(queryInfo(), this, CountType.DOCS, fetchAllResults);
    }
    
    @Override
    public abstract boolean equals(Object obj);
    
    @Override
    public abstract int hashCode();
    
}
