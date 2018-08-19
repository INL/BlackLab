package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsFromHits extends SearchHitGroups {
    
    private SearchHits source;
    
    private HitProperty property;
    
    private int maxHits;

    public SearchHitGroupsFromHits(QueryInfo queryInfo, SearchHits hitsSearch, HitProperty groupBy, int maxResultsToStorePerGroup) {
        super(queryInfo);
        this.source = hitsSearch;
        this.property = groupBy;
        this.maxHits = maxResultsToStorePerGroup;
    }

    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     * @throws InvalidQuery 
     */
    @Override
    public HitGroups executeInternal() throws InvalidQuery {
        return HitGroups.fromHits(source.execute(), property, maxHits);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + maxHits;
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
        if (property == null) {
            if (other.property != null)
                return false;
        } else if (!property.equals(other.property))
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        if (maxHits != other.maxHits)
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return toString("group", source, property, maxHits);
    }
}
