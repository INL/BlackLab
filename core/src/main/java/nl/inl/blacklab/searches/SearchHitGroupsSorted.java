package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsSorted extends SearchHitGroups {
    
    private SearchHitGroups source;
    
    private ResultProperty<HitGroup> sortBy;

    public SearchHitGroupsSorted(QueryInfo queryInfo, List<SearchOperation> ops, SearchHitGroups source, ResultProperty<HitGroup> sortBy) {
        super(queryInfo, ops);
        this.source = source;
        this.sortBy = sortBy;
    }

    @Override
    public HitGroups execute() throws InvalidQuery {
        return performCustom(source.execute().sortedBy(sortBy));
    }

    @Override
    public SearchHitGroupsSorted custom(SearchOperation operation) {
        return new SearchHitGroupsSorted(queryInfo(), extraCustomOp(operation), source, sortBy);
    }
}
