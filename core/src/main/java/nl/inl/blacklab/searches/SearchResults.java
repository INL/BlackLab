package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.Results;

/** A search that yields results. 
 * @param <R> result type, e.g. Hit
 */
public abstract class SearchResults<R> extends AbstractSearch {

    SearchResults(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Execute the search operation, returning the final response.
     * 
     * @return result of the operation
     * @throws InvalidQuery if query is invalid 
     */
    @Override
    public abstract Results<R> execute() throws WildcardTermTooBroad, RegexpTooLarge, InvalidQuery;

    /**
     * Count hits.
     * @return resulting operation
     */
    public SearchCount count() {
        return new SearchCountFromResults<R>(queryInfo(), this);
    }
    
}
