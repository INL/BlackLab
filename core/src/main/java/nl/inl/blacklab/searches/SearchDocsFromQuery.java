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
    public DocResults execute() throws InvalidQuery {
        return queryInfo().index().queryDocuments(query);
    }

}
