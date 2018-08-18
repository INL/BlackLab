package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
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
     * @throws InvalidQuery 
     */
    @Override
    public HitGroups executeInternal() throws InvalidQuery {
        return HitGroups.fromHits(hitsSearch.execute(), groupBy, maxResultsToStorePerGroup);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupBy == null) ? 0 : groupBy.hashCode());
        result = prime * result + ((hitsSearch == null) ? 0 : hitsSearch.hashCode());
        result = prime * result + maxResultsToStorePerGroup;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchHitGroupsFromHits other = (SearchHitGroupsFromHits) obj;
        if (groupBy == null) {
            if (other.groupBy != null)
                return false;
        } else if (!groupBy.equals(other.groupBy))
            return false;
        if (hitsSearch == null) {
            if (other.hitsSearch != null)
                return false;
        } else if (!hitsSearch.equals(other.hitsSearch))
            return false;
        if (maxResultsToStorePerGroup != other.maxResultsToStorePerGroup)
            return false;
        return true;
    }
}
