package nl.inl.blacklab.searches;

import java.util.List;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.MaxSettings;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** A search that yields hits. */
public class SearchHitsFromPattern extends SearchHits {

    private TextPattern pattern;
    
    private Query filter;
    
    private MaxSettings maxSettings;

    SearchHitsFromPattern(QueryInfo queryInfo, List<SearchResultObserver> ops, TextPattern pattern, Query filter, MaxSettings maxSettings) {
        super(queryInfo, ops);
        this.pattern = pattern;
        this.filter = filter;
        this.maxSettings = maxSettings;
    }

    /**
     * Execute the search operation, returning the final response.
     * 
     * @return result of the operation
     * @throws RegexpTooLarge if a regular expression was too large
     * @throws WildcardTermTooBroad if a wildcard term or regex matched too many terms
     */
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return notifyObservers(queryInfo().index().find(pattern, queryInfo().field(), filter, maxSettings));
    }

    @Override
    public SearchHitsFromPattern observe(SearchResultObserver operation) {
        return new SearchHitsFromPattern(queryInfo(), extraObserver(operation), pattern, filter, maxSettings);
    }
}
