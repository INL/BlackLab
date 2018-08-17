package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;

/**
 * A search operation that yields a count as its result.
 * @param <T> result type, e.g. Hit
 */
public class SearchCountFromResults<T> extends SearchCount {
    
    private SearchResults<T> source;

    public SearchCountFromResults(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchResults<T> source) {
        super(queryInfo, ops);
        this.source = source;
    }

    @Override
    public ResultCount execute() throws InvalidQuery {
        return notifyObservers(new ResultCount(source.execute().size()));
    }

    @Override
    public SearchCountFromResults<T> observe(SearchResultObserver op) {
        return new SearchCountFromResults<T>(queryInfo(), extraObserver(op), source);
    }

}
