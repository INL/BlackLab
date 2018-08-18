package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsWindow extends SearchCollocations {

    private SearchCollocations source;
    private int first;
    private int number;

    public SearchCollocationsWindow(QueryInfo queryInfo, SearchCollocations source, int first, int number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return source.execute().window(first, number);
    }

}
