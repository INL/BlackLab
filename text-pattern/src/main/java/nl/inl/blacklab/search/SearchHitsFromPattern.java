package nl.inl.blacklab.search;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchHits;

/** A search that yields hits.
 * @deprecated use SearchHitsFromBLSpanQuery instead
 */
@Deprecated
class SearchHitsFromPattern extends SearchHits {

    private TextPattern pattern;
    
    private Query filter;

    private SearchSettings searchSettings;

    public SearchHitsFromPattern(QueryInfo queryInfo, TextPattern pattern, Query filter, SearchSettings searchSettings) {
        super(queryInfo);
        if (pattern == null)
            throw new IllegalArgumentException("Must specify a pattern");
        this.pattern = pattern;
        this.filter = filter;
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
        BLSpanQuery spanQuery = pattern.translate(queryInfo().index().defaultExecutionContext(queryInfo().field()));
        if (filter != null)
            spanQuery = new SpanQueryFiltered(spanQuery, filter);
        return queryInfo().index().find(spanQuery, searchSettings, queryInfo().searchLogger());
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        result = prime * result + ((pattern == null) ? 0 : pattern.hashCode());
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
        SearchHitsFromPattern other = (SearchHitsFromPattern) obj;
        if (filter == null) {
            if (other.filter != null)
                return false;
        } else if (!filter.equals(other.filter))
            return false;
        if (pattern == null) {
            if (other.pattern != null)
                return false;
        } else if (!pattern.equals(other.pattern))
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
        if (filter == null)
            return toString("hits", pattern);
        return toString("hits", pattern, filter);
    }
}
