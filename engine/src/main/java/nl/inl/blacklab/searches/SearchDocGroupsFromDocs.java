package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of documents. */
public class SearchDocGroupsFromDocs extends SearchDocGroups {

    private SearchDocs source;

    private DocProperty property;

    private int maxDocs;

    public SearchDocGroupsFromDocs(QueryInfo queryInfo, SearchDocs source, DocProperty property, int maxDocsToStorePerGroup) {
        super(queryInfo);
        this.source = source;
        this.property = property;
        this.maxDocs = maxDocsToStorePerGroup;
    }

    @Override
    public DocGroups executeInternal() throws InvalidQuery {
        return source.executeNoQueue().group(property, maxDocs);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + maxDocs;
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchDocGroupsFromDocs other = (SearchDocGroupsFromDocs) obj;
        if (maxDocs != other.maxDocs)
            return false;
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
        return toString("group", source, property, maxDocs);
    }

}
