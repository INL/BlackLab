package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFromHits extends SearchDocs {

    private SearchHits hitsSearch;

    private int maxHits = 0;

    public SearchDocsFromHits(QueryInfo queryInfo, List<SearchOperation> customOperations, SearchHits hitSearch, int maxHitsToGatherPerDocument) {
        super(queryInfo, customOperations);
        this.hitsSearch = hitSearch;
        this.maxHits = maxHitsToGatherPerDocument;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return performCustom(hitsSearch.execute().perDocResults(maxHits));
    }

    @Override
    public SearchDocsFromHits custom(SearchOperation operation) {
        return new SearchDocsFromHits(queryInfo(), extraCustomOp(operation), hitsSearch, maxHits);
    }

}
