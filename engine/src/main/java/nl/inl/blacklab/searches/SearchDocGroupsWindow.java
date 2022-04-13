package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of documents. */
public class SearchDocGroupsWindow extends SearchDocGroups {

    private SearchDocGroups source;
    private long first;
    private long number;

    public SearchDocGroupsWindow(QueryInfo queryInfo, SearchDocGroups source, long first, long number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public DocGroups executeInternal(Peekable<DocGroups> progressReporter) throws InvalidQuery {
        return source.executeNoQueue().window(first, number);
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
        SearchDocGroupsWindow other = (SearchDocGroupsWindow) obj;
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
