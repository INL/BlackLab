package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFromHits extends SearchDocs {

    private SearchHits hitsSearch;

    private int maxHits = 0;

    public SearchDocsFromHits(QueryInfo queryInfo, List<SearchResultObserver> customOperations, SearchHits hitSearch, int maxHitsToGatherPerDocument) {
        super(queryInfo, customOperations);
        this.hitsSearch = hitSearch;
        this.maxHits = maxHitsToGatherPerDocument;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return notifyObservers(hitsSearch.execute().perDocResults(maxHits));
    }

    @Override
    public SearchDocsFromHits observe(SearchResultObserver operation) {
        return new SearchDocsFromHits(queryInfo(), extraObserver(operation), hitsSearch, maxHits);
    }

}
