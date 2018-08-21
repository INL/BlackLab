package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.search.textpattern.TextPattern;

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

    public SearchHits find(String cql, Query filter, SearchSettings searchSettings) throws InvalidQuery {
        return find(CorpusQueryLanguageParser.parse(cql), filter, searchSettings);
    }

    public SearchHits find(TextPattern pattern, Query filter, SearchSettings searchSettings) {
        return new SearchHitsFromPattern(queryInfo(), pattern, filter, searchSettings);
    }
    
    public SearchDocs find(Query documentQuery) {
        return new SearchDocsFromQuery(queryInfo(), documentQuery);
    }
    
    @Override
    public String toString() {
        return toString("empty");
    }
    
}
