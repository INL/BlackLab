package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFromQuery extends SearchDocs {

    private Query query;

    public SearchDocsFromQuery(QueryInfo queryInfo, Query query) {
        super(queryInfo);
        this.query = query;
    }

    @Override
    public DocResults executeInternal() throws InvalidQuery {
        return queryInfo().index().queryDocuments(query);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((query == null) ? 0 : query.hashCode());
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
        SearchDocsFromQuery other = (SearchDocsFromQuery) obj;
        if (query == null) {
            if (other.query != null)
                return false;
        } else if (!query.equals(other.query))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("docquery", query);
    }

}
