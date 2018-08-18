package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of docs. */
public class SearchDocGroupsSorted extends SearchDocGroups {
    
    private SearchDocGroups source;

    private DocGroupProperty property;

    public SearchDocGroupsSorted(QueryInfo queryInfo, SearchDocGroups source, DocGroupProperty property) {
        super(queryInfo);
        this.source = source;
        this.property = property;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return notifyCache(source.execute().sortedBy(property));
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
        SearchDocGroupsSorted other = (SearchDocGroupsSorted) obj;
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
}
