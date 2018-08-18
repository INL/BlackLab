package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFiltered extends SearchDocs {

    private SearchDocs docsSearch;

    private DocProperty property;

    private PropertyValue value;

    public SearchDocsFiltered(QueryInfo queryInfo, SearchDocs docsSearch, DocProperty sortBy, PropertyValue value) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.property = sortBy;
        this.value = value;
    }

    @Override
    public DocResults executeInternal() throws InvalidQuery {
        return docsSearch.execute().filter(property, value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((docsSearch == null) ? 0 : docsSearch.hashCode());
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        SearchDocsFiltered other = (SearchDocsFiltered) obj;
        if (docsSearch == null) {
            if (other.docsSearch != null)
                return false;
        } else if (!docsSearch.equals(other.docsSearch))
            return false;
        if (property == null) {
            if (other.property != null)
                return false;
        } else if (!property.equals(other.property))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

}
