package nl.inl.blacklab.searches;

import java.util.List;

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

    public SearchCollocationsWindow(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchCollocations source, int first, int number) {
        super(queryInfo, ops);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return notifyObservers(source.execute().window(first, number));
    }

    @Override
    public SearchCollocationsWindow observe(SearchResultObserver observer) {
        return new SearchCollocationsWindow(queryInfo(), extraObserver(observer), source, first, number);
    }

}
