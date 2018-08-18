package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFromHits extends SearchDocs {

    private SearchHits hitsSearch;

    private int maxHits = 0;

    public SearchDocsFromHits(QueryInfo queryInfo, SearchHits hitSearch, int maxHitsToGatherPerDocument) {
        super(queryInfo);
        this.hitsSearch = hitSearch;
        this.maxHits = maxHitsToGatherPerDocument;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return hitsSearch.execute().perDocResults(maxHits);
    }

}
