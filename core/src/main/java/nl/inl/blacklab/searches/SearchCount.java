package nl.inl.blacklab.searches;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchCount extends AbstractSearch {
    
    public SearchCount(QueryInfo queryInfo) {
        super(queryInfo);
    }
    
    @Override
    public final ResultCount execute() throws InvalidQuery {
        CompletableFuture<ResultCount> future = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<ResultCount> fromCache = (CompletableFuture<ResultCount>)getFromCache(this, future);
        if (fromCache != null) {
            try {
                return fromCache.get();
            } catch (InterruptedException | ExecutionException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
        try {
            ResultCount result = executeInternal();
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
    
    protected abstract ResultCount executeInternal() throws InvalidQuery;

    
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
