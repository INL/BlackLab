package nl.inl.blacklab.searches;

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
    
    private ResultProperty<HitGroup> property;

    public SearchHitGroupsSorted(QueryInfo queryInfo, SearchHitGroups source, ResultProperty<HitGroup> sortBy) {
        super(queryInfo);
        this.source = source;
        this.property = sortBy;
    }

    @Override
    public HitGroups executeInternal() throws InvalidQuery {
        return source.execute().sort(property);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
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
        SearchHitGroupsSorted other = (SearchHitGroupsSorted) obj;
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
        return true;
    }
    
    @Override
    public String toString() {
        return toString("sort", source, property);
    }
}
