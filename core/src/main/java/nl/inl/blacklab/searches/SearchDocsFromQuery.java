package nl.inl.blacklab.searches;

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFromQuery extends SearchDocs {

    private Query query;

    public SearchDocsFromQuery(QueryInfo queryInfo, List<SearchOperation> customOperations, Query query) {
        super(queryInfo, customOperations);
        this.query = query;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return performCustom(queryInfo().index().queryDocuments(query));
    }

    @Override
    public SearchDocsFromQuery custom(SearchOperation operation) {
        return new SearchDocsFromQuery(queryInfo(), extraCustomOp(operation), query);
    }

}
