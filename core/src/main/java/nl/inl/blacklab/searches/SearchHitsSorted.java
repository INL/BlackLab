package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields hits. */
public class SearchHitsSorted extends SearchHits {

    private SearchHits source;
    private HitProperty sortBy;

    SearchHitsSorted(QueryInfo queryInfo, List<SearchOperation> ops, SearchHits source, HitProperty sortBy) {
        super(queryInfo, ops);
        this.source = source;
        this.sortBy = sortBy;
    }
    
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return performCustom(source.execute().sortedBy(sortBy));
    }

    @Override
    public SearchHitsSorted custom(SearchOperation operation) {
        return new SearchHitsSorted(queryInfo(), extraCustomOp(operation), source, sortBy);
    }
}
