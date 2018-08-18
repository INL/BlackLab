package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsSorted extends SearchDocs {

    private SearchDocs docsSearch;

    private DocProperty sortBy;

    public SearchDocsSorted(QueryInfo queryInfo, SearchDocs docsSearch, DocProperty sortBy) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.sortBy = sortBy;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return notifyCache(docsSearch.execute().sortedBy(sortBy));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((docsSearch == null) ? 0 : docsSearch.hashCode());
        result = prime * result + ((sortBy == null) ? 0 : sortBy.hashCode());
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
        SearchDocsSorted other = (SearchDocsSorted) obj;
        if (docsSearch == null) {
            if (other.docsSearch != null)
                return false;
        } else if (!docsSearch.equals(other.docsSearch))
            return false;
        if (sortBy == null) {
            if (other.sortBy != null)
                return false;
        } else if (!sortBy.equals(other.sortBy))
            return false;
        return true;
    }

}
