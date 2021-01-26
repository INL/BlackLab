package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;

/** A search that yields hits. */
public class SearchHitsFromBLSpanQuery extends SearchHits {

    private BLSpanQuery spanQuery;

    private SearchSettings searchSettings;

    public SearchHitsFromBLSpanQuery(QueryInfo queryInfo, BLSpanQuery spanQuery, SearchSettings searchSettings) {
        super(queryInfo);
        if (spanQuery == null)
            throw new IllegalArgumentException("Must specify a query");
        this.spanQuery = spanQuery;
        this.searchSettings = searchSettings;
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * @return result of the operation
     * @throws RegexpTooLarge if a regular expression was too large
     * @throws WildcardTermTooBroad if a wildcard term or regex matched too many terms
     */
    @Override
    public Hits executeInternal() throws InvalidQuery {
        return queryInfo().index().find(spanQuery, searchSettings, queryInfo().searchLogger());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((spanQuery == null) ? 0 : spanQuery.hashCode());
        result = prime * result + ((searchSettings == null) ? 0 : searchSettings.hashCode());
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
        SearchHitsFromBLSpanQuery other = (SearchHitsFromBLSpanQuery) obj;
        if (spanQuery == null) {
            if (other.spanQuery != null)
                return false;
        } else if (!spanQuery.equals(other.spanQuery))
            return false;
        if (searchSettings == null) {
            if (other.searchSettings != null)
                return false;
        } else if (!searchSettings.equals(other.searchSettings))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("hits", spanQuery);
    }

    public BLSpanQuery query() {
        return spanQuery;
    }

    @Override
    public boolean isAnyTokenQuery() {
        return spanQuery.isSingleAnyToken();
    }

    @Override
    protected Query getFilterQuery() {
        return spanQuery;
    }

    @Override
    protected SearchSettings searchSettings() {
        return searchSettings;
    }
}
