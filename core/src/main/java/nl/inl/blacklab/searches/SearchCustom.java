package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A custom search action.
 * 
 * @param <S> source search type
 * @param <T> target result type
 */
public abstract class SearchCustom<S extends Search, T extends SearchResult> extends AbstractSearch {

    public SearchCustom(QueryInfo queryInfo, List<SearchResultObserver> customOperations) {
        super(queryInfo, customOperations);
    }
    
    /**
     * Apply this operation to the source search.
     * 
     * @param source search to apply this operation to
     * @return new search operation
     */
    public abstract SearchCustom<S, T> apply(S source);

    @Override
    public abstract T execute() throws InvalidQuery;

    @Override
    public abstract SearchCustom<S, T> observe(SearchResultObserver operation);

}
