package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;

/** A search that yields hits. */
public class SearchHitsWindow extends SearchHits {

    private SearchHits source;
    private int first;
    private int number;

    SearchHitsWindow(QueryInfo queryInfo, SearchHits source, int first, int number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public Hits executeInternal() throws InvalidQuery {
        return source.executeNoQueue().window(first, number);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + first;
        result = prime * result + number;
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
        SearchHitsWindow other = (SearchHitsWindow) obj;
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

    @Override
    protected SearchSettings searchSettings() {
        return source.searchSettings();
    }
}
