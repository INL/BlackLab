package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsFromHits extends SearchHitGroups {
    
    private SearchHits hitsSearch;
    
    private HitProperty groupBy;
    
    private int maxResultsToStorePerGroup;

    public SearchHitGroupsFromHits(QueryInfo queryInfo, SearchHits hitsSearch, HitProperty groupBy, int maxResultsToStorePerGroup) {
        super(queryInfo);
        this.hitsSearch = hitsSearch;
        this.groupBy = groupBy;
        this.maxResultsToStorePerGroup = maxResultsToStorePerGroup;
    }

    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     * @throws RegexpTooLarge if a regular expression was too large 
     * @throws WildcardTermTooBroad if a term expression matched too many terms
     */
    @Override
    public HitGroups execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return HitGroups.fromHits(hitsSearch.execute(), groupBy, maxResultsToStorePerGroup);
    }
}
