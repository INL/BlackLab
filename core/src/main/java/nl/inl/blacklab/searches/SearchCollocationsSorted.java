package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsSorted extends SearchCollocations {

    private SearchCollocations source;
    private ResultProperty<TermFrequency> property;

    public SearchCollocationsSorted(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchCollocations source, ResultProperty<TermFrequency> property) {
        super(queryInfo, ops);
        this.source = source;
        this.property = property;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return notifyObservers(source.execute().sortedBy(property));
    }

    @Override
    public SearchCollocationsSorted observe(SearchResultObserver observer) {
        return new SearchCollocationsSorted(queryInfo(), extraObserver(observer), source, property);
    }

}
