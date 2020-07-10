package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/** 
 * Empty search that just knows about its index and annotated field to search,
 * and serves as a starting point for actual searches.
 */
public class SearchEmpty extends AbstractSearch<SearchResult> {
    
    public SearchEmpty(QueryInfo queryInfo) {
        super(queryInfo);
    }

    @Override
    protected SearchResult executeInternal() throws InvalidQuery {
        throw new UnsupportedOperationException();
    }
    
    public SearchDocs find(Query documentQuery) {
        return new SearchDocsFromQuery(queryInfo(), documentQuery);
    }
    
    @Override
    public String toString() {
        return toString("empty");
    }
    
}
