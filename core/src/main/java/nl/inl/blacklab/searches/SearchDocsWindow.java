package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsWindow extends SearchDocs {

    private SearchDocs docsSearch;

    private int first;

    private int number;

    public SearchDocsWindow(QueryInfo queryInfo, SearchDocs docsSearch, int first, int number) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.first = first;
        this.number = number;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return docsSearch.execute().window(first, number);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((docsSearch == null) ? 0 : docsSearch.hashCode());
        result = prime * result + first;
        result = prime * result + number;
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
        SearchDocsWindow other = (SearchDocsWindow) obj;
        if (docsSearch == null) {
            if (other.docsSearch != null)
                return false;
        } else if (!docsSearch.equals(other.docsSearch))
            return false;
        if (first != other.first)
            return false;
        if (number != other.number)
            return false;
        return true;
    }

}
