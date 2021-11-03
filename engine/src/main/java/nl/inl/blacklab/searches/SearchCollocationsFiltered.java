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
    public TermFrequencyList executeInternal() throws InvalidQuery {
        return source.executeNoQueue().filter(property, value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchCollocationsFiltered other = (SearchCollocationsFiltered) obj;
        if (property == null) {
            if (other.property != null)
                return false;
        } else if (!property.equals(other.property))
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("filter", source, property, value);
    }
}
