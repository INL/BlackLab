package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;

/**
 * A search operation that yields a count as its result.
 */
public class SearchCountFromHits extends SearchCount {
    
    private SearchHits source;

    public SearchCountFromHits(QueryInfo queryInfo, List<SearchOperation> ops, SearchHits source) {
        super(queryInfo, ops);
        this.source = source;
    }

    @Override
    public ResultCount execute() throws InvalidQuery {
        return performCustom(new ResultCount(source.execute().size()));
    }

    @Override
    public SearchCountFromHits custom(SearchOperation op) {
        return new SearchCountFromHits(queryInfo(), extraCustomOp(op), source);
    }

}
