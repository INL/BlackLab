package nl.inl.blacklab.tmputil;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.searches.SearchHitsFromPattern;

public class SearchMethods {

    public static SearchHits find(SearchEmpty search, String cql, Query filter, SearchSettings searchSettings) throws InvalidQuery {
        return find(search, CorpusQueryLanguageParser.parse(cql), filter, searchSettings);
    }

    public static SearchHits find(SearchEmpty search, TextPattern pattern, Query filter, SearchSettings searchSettings) {
        return new SearchHitsFromPattern(search.queryInfo(), pattern, filter, searchSettings);
    }
}
