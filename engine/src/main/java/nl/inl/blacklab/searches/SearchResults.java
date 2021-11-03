package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount.CountType;
import nl.inl.blacklab.search.results.Results;

/** A search that yields results.
 * @param <R> results type, e.g. Hits
 */
public abstract class SearchResults<R extends Results<?, ?>> extends AbstractSearch<R> {

    SearchResults(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Count hits. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount count() {
        return new SearchCountFromResults<>(queryInfo(), this, CountType.RESULTS);
    }

    /**
     * Count hits. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount hitCount() {
        return new SearchCountFromResults<>(queryInfo(), this, CountType.HITS);
    }

    /**
     * Count docs. All results are fetched in the cache thread.
     *
     * @return resulting operation
     */
    public SearchCount docCount() {
        return new SearchCountFromResults<>(queryInfo(), this, CountType.DOCS);
    }

}
