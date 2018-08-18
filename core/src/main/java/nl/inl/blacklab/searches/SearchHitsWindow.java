package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields hits. */
public class SearchHitsWindow extends SearchHits {

    private SearchHits source;
    private int first;
    private int number;

    SearchHitsWindow(QueryInfo queryInfo, SearchHits source, int first, int number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }
    
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return source.execute().window(first, number);
    }
}
