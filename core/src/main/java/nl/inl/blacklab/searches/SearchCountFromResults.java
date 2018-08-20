package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.Results;

/**
 * A search operation that yields a count as its result.
 * @param <T> result type, e.g. Hit
 */
public class SearchCountFromResults<T extends Results<?>> extends SearchCount {
    
    private SearchResults<T> source;

    public SearchCountFromResults(QueryInfo queryInfo, SearchResults<T> source) {
        super(queryInfo);
        this.source = source;
    }

    @Override
    public ResultCount executeInternal() throws InvalidQuery {
        return new ResultCount(source.execute().size());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SearchCountFromResults<?> other = (SearchCountFromResults<?>) obj;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return toString("count", source);
    }

}
