package nl.inl.blacklab.searches;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * Abstract base class for all Search implementations,
 * to enforce that equals() and hashCode are implemented
 * (to ensure proper caching)
 * 
 * @param <R> results type, e.g. Hits
 */
public abstract class AbstractSearch<R extends SearchResult> implements Search {
	
    private QueryInfo queryInfo;
    
    public AbstractSearch(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;  
    }
    
    @Override
    public final R execute() throws InvalidQuery {
        CompletableFuture<R> future = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<R> fromCache = (CompletableFuture<R>)getFromCache(this, future);
        if (fromCache != null) {
            try {
                return fromCache.get();
            } catch (InterruptedException | ExecutionException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
        try {
            R result = executeInternal();
            future.complete(result);
            return result;
        } catch (InvalidQuery e) {
            cancelSearch(future);
            throw e;
        } catch (RuntimeException e) {
            cancelSearch(future);
            throw e;
        }
    }
    
    protected abstract R executeInternal() throws InvalidQuery;
    
    protected CompletableFuture<? extends SearchResult> getFromCache(Search search, CompletableFuture<? extends SearchResult> future) {
        return queryInfo.index().cache().get(search, future);
    }
    
    protected void cancelSearch(CompletableFuture<? extends SearchResult> future) {
        queryInfo.index().cache().remove(this);
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
        AbstractSearch<?> other = (AbstractSearch<?>) obj;
        if (queryInfo == null) {
            if (other.queryInfo != null)
                return false;
        } else if (!queryInfo.equals(other.queryInfo))
            return false;
        return true;
    }
	
}
