package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsWindow extends SearchDocs {

    private SearchDocs source;

    private int first;

    private int number;

    public SearchDocsWindow(QueryInfo queryInfo, SearchDocs docsSearch, int first, int number) {
        super(queryInfo);
        this.source = docsSearch;
        this.first = first;
        this.number = number;
    }

    @Override
    public DocResults executeInternal() throws InvalidQuery {
        return source.executeNoQueue().window(first, number);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + first;
        result = prime * result + number;
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
        SearchDocsWindow other = (SearchDocsWindow) obj;
        if (first != other.first)
            return false;
        if (number != other.number)
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
        return toString("window", source, first, number);
    }

}
