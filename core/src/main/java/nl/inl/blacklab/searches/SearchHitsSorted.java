package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields hits. */
public class SearchHitsSorted extends SearchHits {

    private SearchHits source;
    private HitProperty sortBy;

    SearchHitsSorted(QueryInfo queryInfo, SearchHits source, HitProperty sortBy) {
        super(queryInfo);
        this.source = source;
        this.sortBy = sortBy;
    }
    
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return source.execute().sortedBy(sortBy);
    }
}
