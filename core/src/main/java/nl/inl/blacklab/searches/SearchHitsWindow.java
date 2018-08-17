package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields hits. */
public class SearchHitsWindow extends SearchHits {

    private SearchHits source;
    private int first;
    private int number;

    SearchHitsWindow(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchHits source, int first, int number) {
        super(queryInfo, ops);
        this.source = source;
        this.first = first;
        this.number = number;
    }
    
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return notifyObservers(source.execute().window(first, number));
    }

    @Override
    public SearchHitsWindow observe(SearchResultObserver operation) {
        return new SearchHitsWindow(queryInfo(), extraObserver(operation), source, first, number);
    }
}
