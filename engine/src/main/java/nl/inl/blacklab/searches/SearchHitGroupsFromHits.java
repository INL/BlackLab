package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.HitGroupsTokenFrequencies;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsFromHits extends SearchHitGroups {

    private SearchHits source;

    private HitProperty property;

    private int maxResultsToStorePerGroup;

    private boolean useFastPath = false;

    public SearchHitGroupsFromHits(QueryInfo queryInfo, SearchHits hitsSearch, HitProperty groupBy, int maxResultsToStorePerGroup) {
        super(queryInfo);
        this.source = hitsSearch;
        this.property = groupBy;
        this.maxResultsToStorePerGroup = maxResultsToStorePerGroup;
        this.useFastPath = fastPathAvailable();
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * @return result of the operation
     * @throws InvalidQuery
     */
    @Override
    public HitGroups executeInternal() throws InvalidQuery {
        if (useFastPath)
            return HitGroups.tokenFrequencies(source.queryInfo(), source.getFilterQuery(), source.searchSettings(), property, maxResultsToStorePerGroup);
        else  // Just find all the hits and group them.
            return HitGroups.fromHits(source.executeNoQueue(), property, maxResultsToStorePerGroup);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + maxResultsToStorePerGroup;
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + Boolean.hashCode(useFastPath);
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
        SearchHitGroupsFromHits other = (SearchHitGroupsFromHits) obj;
        if (maxResultsToStorePerGroup != other.maxResultsToStorePerGroup)
            return false;
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
        if (other.useFastPath != useFastPath)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("group", source, property, maxResultsToStorePerGroup);
    }

    private boolean fastPathAvailable() {
        // Any token query! Choose faster path that just "looks up"
        // token frequencies in the forward index(es).
        return HitGroupsTokenFrequencies.TOKEN_FREQUENCIES_FAST_PATH_IMPLEMENTED && source.isAnyTokenQuery() && property.isDocPropOrHitText();
    }

    /**
     * When using the fast path, backing hits are not stored in the groups.
     * This saves a large amount of memory and time, but transforms the query into more of a statistical view on the data
     * because the individual hits are lost.
     * Call this to disable the optimizations and store all Hits (up to maxResultsToStorePerGroup).
     */
    public void forceStoreHits() {
        this.useFastPath = false;
    }
}
