package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsFiltered extends SearchCollocations {

    private SearchCollocations source;
    
    private ResultProperty<TermFrequency> property;
    
    private PropertyValue value;

    public SearchCollocationsFiltered(QueryInfo queryInfo, SearchCollocations source, ResultProperty<TermFrequency> property, PropertyValue value) {
        super(queryInfo);
        this.source = source;
        this.property = property;
        this.value = value;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return source.execute().filteredBy(property, value);
    }
}
