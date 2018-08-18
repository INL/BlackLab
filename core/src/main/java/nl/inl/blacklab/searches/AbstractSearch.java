package nl.inl.blacklab.searches;

import java.util.concurrent.CompletableFuture;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * Abstract base class for all Search implementations,
 * to enforce that equals() and hashCode are implemented
 * (to ensure proper caching)
 */
public abstract class AbstractSearch implements Search {
	
    private QueryInfo queryInfo;
    
    public AbstractSearch(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;  
    }
    
    protected CompletableFuture<? extends SearchResult> getFromCache(Search search, CompletableFuture<? extends SearchResult> future) {
        SearchCache cache = queryInfo.index().cache();
        CompletableFuture<? extends SearchResult> fromCache = cache.get(search, future);
        if (fromCache == null) {
            cache.onSearchResult(this, future);
        }
        return fromCache;
    }
    
    protected void cancelSearch(CompletableFuture<? extends SearchResult> future) {
        queryInfo.index().cache().remove(future);
        future.cancel(false);
    }
    
    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((queryInfo == null) ? 0 : queryInfo.hashCode());
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
        AbstractSearch other = (AbstractSearch) obj;
        if (queryInfo == null) {
            if (other.queryInfo != null)
                return false;
        } else if (!queryInfo.equals(other.queryInfo))
            return false;
        return true;
    }
	
}
