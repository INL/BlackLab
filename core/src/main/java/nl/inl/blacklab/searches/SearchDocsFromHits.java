package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFromHits extends SearchDocs {

    private SearchHits source;

    private int maxHits = 0;

    public SearchDocsFromHits(QueryInfo queryInfo, SearchHits hitSearch, int maxHitsToGatherPerDocument) {
        super(queryInfo);
        this.source = hitSearch;
        this.maxHits = maxHitsToGatherPerDocument;
    }

    @Override
    protected DocResults executeInternal() throws InvalidQuery {
        return source.execute().perDocResults(maxHits);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + maxHits;
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
        SearchDocsFromHits other = (SearchDocsFromHits) obj;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        if (maxHits != other.maxHits)
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return toString("docs", source, maxHits);
    }

}
