package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.Results;

/**
 * Given a (running) SearchCount, determine the final total count.
 * @param <T> result type, e.g. Hit
 */
public class SearchCountTotal<T extends Results<?, ?>> extends SearchCount {

    private ResultCount source;

    public SearchCountTotal(QueryInfo queryInfo, ResultCount source) {
        super(queryInfo);
        this.source = source;
    }

    @Override
    public ResultCount executeInternal() throws InvalidQuery {
        source.processedTotal();
        return source;
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
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        @SuppressWarnings("unchecked")
        SearchCountTotal<T> other = (SearchCountTotal<T>) obj;
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
