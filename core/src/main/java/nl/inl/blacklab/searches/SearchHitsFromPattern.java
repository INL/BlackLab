package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.MaxSettings;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** A search that yields hits. */
public class SearchHitsFromPattern extends SearchHits {

    private TextPattern pattern;
    
    private Query filter;
    
    private MaxSettings maxSettings;

    SearchHitsFromPattern(QueryInfo queryInfo, TextPattern pattern, Query filter, MaxSettings maxSettings) {
        super(queryInfo);
        this.pattern = pattern;
        this.filter = filter;
        this.maxSettings = maxSettings;
    }

    /**
     * Execute the search operation, returning the final response.
     * 
     * @return result of the operation
     * @throws RegexpTooLarge if a regular expression was too large
     * @throws WildcardTermTooBroad if a wildcard term or regex matched too many terms
     */
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return notifyCache(queryInfo().index().find(pattern, queryInfo().field(), filter, maxSettings));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        result = prime * result + ((maxSettings == null) ? 0 : maxSettings.hashCode());
        result = prime * result + ((pattern == null) ? 0 : pattern.hashCode());
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
        SearchHitsFromPattern other = (SearchHitsFromPattern) obj;
        if (filter == null) {
            if (other.filter != null)
                return false;
        } else if (!filter.equals(other.filter))
            return false;
        if (maxSettings == null) {
            if (other.maxSettings != null)
                return false;
        } else if (!maxSettings.equals(other.maxSettings))
            return false;
        if (pattern == null) {
            if (other.pattern != null)
                return false;
        } else if (!pattern.equals(other.pattern))
            return false;
        return true;
    }
}
