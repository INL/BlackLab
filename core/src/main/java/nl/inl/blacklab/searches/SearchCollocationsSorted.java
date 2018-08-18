package nl.inl.blacklab.searches;

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

    public SearchCollocationsSorted(QueryInfo queryInfo, SearchCollocations source, ResultProperty<TermFrequency> property) {
        super(queryInfo);
        this.source = source;
        this.property = property;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return source.execute().sortedBy(property);
    }

}
