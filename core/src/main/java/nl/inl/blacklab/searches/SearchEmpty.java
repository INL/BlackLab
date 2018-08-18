package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.results.MaxSettings;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** 
 * Empty search that just knows about its index and annotated field to search,
 * and serves as a starting point for actual searches.
 */
public class SearchEmpty extends AbstractSearch {
    
    public SearchEmpty(QueryInfo queryInfo) {
        super(queryInfo);
    }

    @Override
    public SearchResult execute() {
        throw new UnsupportedOperationException();
    }

    public SearchHits find(String cql, Query filter, MaxSettings maxSettings) throws InvalidQuery {
        return find(CorpusQueryLanguageParser.parse(cql), filter, maxSettings);
    }

    public SearchHits find(TextPattern pattern, Query filter, MaxSettings maxSettings) {
        return new SearchHitsFromPattern(queryInfo(), pattern, filter, maxSettings);
    }
    
    public SearchDocs find(Query documentQuery) {
        return new SearchDocsFromQuery(queryInfo(), documentQuery);
    }
    
}
