package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsWindow extends SearchCollocations {

    private final SearchCollocations source;
    private final long first;
    private final long number;

    public SearchCollocationsWindow(QueryInfo queryInfo, SearchCollocations source, long first, long number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public TermFrequencyList executeInternal(SearchTask<TermFrequencyList> searchTask) throws InvalidQuery {
        return executeChildSearch(searchTask, source).window(first, number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, first, number);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchCollocationsWindow other = (SearchCollocationsWindow) obj;
        if (first != other.first)
            return false;
        if (number != other.number)
            return false;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("window", source, first, number);
    }

}
