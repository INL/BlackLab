package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFromHits extends SearchDocs {

    private SearchHits hitsSearch;

    private int maxHits = 0;

    public SearchDocsFromHits(QueryInfo queryInfo, SearchHits hitSearch, int maxHitsToGatherPerDocument) {
        super(queryInfo);
        this.hitsSearch = hitSearch;
        this.maxHits = maxHitsToGatherPerDocument;
    }

    @Override
    public DocResults executeInternal() throws InvalidQuery {
        return hitsSearch.execute().perDocResults(maxHits);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((hitsSearch == null) ? 0 : hitsSearch.hashCode());
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
        if (hitsSearch == null) {
            if (other.hitsSearch != null)
                return false;
        } else if (!hitsSearch.equals(other.hitsSearch))
            return false;
        if (maxHits != other.maxHits)
            return false;
        return true;
    }

}
