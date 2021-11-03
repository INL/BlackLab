package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.search.results.SearchSettings;

/** 
 * Empty search that just knows about its index and annotated field to search,
 * and serves as a starting point for actual searches.
 */
public class SearchEmpty extends AbstractSearch<SearchResult> {
    
    public SearchEmpty(QueryInfo queryInfo) {
        super(queryInfo);
    }

    @Override
    public SearchResult executeInternal() throws InvalidQuery {
        throw new UnsupportedOperationException();
    }

    public SearchHits find(BLSpanQuery query, SearchSettings searchSettings) {
        return new SearchHitsFromBLSpanQuery(queryInfo(), query, searchSettings);
    }

    public SearchHits find(BLSpanQuery query) {
        return find(query, null);
    }
    
    public SearchDocs findDocuments(Query documentQuery) {
        return new SearchDocsFromQuery(queryInfo(), documentQuery);
    }
    
    @Override
    public String toString() {
        return toString("empty");
    }
    
}
