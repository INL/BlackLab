package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.ResultCount.CountType;
import nl.inl.blacklab.search.results.Results;

/**
 * A search operation that yields a count as its result.
 * @param <T> result type, e.g. Hit
 */
public class SearchCountFromResults<T extends Results<?, ?>> extends SearchCount {

    private SearchResults<T> source;
    private CountType type;

    public SearchCountFromResults(QueryInfo queryInfo, SearchResults<T> source, CountType type) {
        super(queryInfo);
        this.source = source;
        this.type = type;
    }

    @Override
    public ResultCount executeInternal() throws InvalidQuery {
        ResultCount resultCount = new ResultCount(source.executeNoQueue(), type);

        // Ensure that the all hits will be counted in a separate thread.
        // (Why a separate thread? Because SearchCountFromResults immediately returns its
        //  result object, and the caller can monitor this object to see the running total
        //  while it is being counted)
        SearchCountTotal<Results<?, ?>> searchCountTotal = new SearchCountTotal<>(queryInfo(), resultCount);
        searchCountTotal.executeAsyncNoQueue();

        return resultCount;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        @SuppressWarnings("unchecked")
        SearchCountFromResults<T> other = (SearchCountFromResults<T>) obj;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("count", source, type);
    }

}
