package nl.inl.blacklab.searches;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import nl.inl.blacklab.search.results.SearchResult;

/**
 * A cache containing [future] results for searches.
 * 
 * Searches are executed in their own thread and may be interrupted half-way.
 * See {@link FutureSearchResult}.
 */
public class FutureSearchResultCache implements SearchCache {

    protected Map<Search, Future<? extends SearchResult>> searches = new HashMap<>();
    
    protected boolean trace = false;

    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    @Override
    public Future<? extends SearchResult> getAsync(Search search, Supplier<? extends SearchResult> searchTask) {
        Future<? extends SearchResult> future;
        synchronized (searches) {
            future = searches.get(search);
            if (future == null) {
                future = new FutureSearchResult<>(searchTask);
                searches.put(search, future);
                if (trace)
                    System.out.println("ADDED: " + search);
            } else {
                if (trace)
                    System.out.println("FOUND: " + search);
            }
        }
        return future;
    }

    @Override
    public Future<? extends SearchResult> remove(Search search) {
        Future<? extends SearchResult> future;
        synchronized (searches) {
            future = searches.remove(search);
            if (trace)
                System.out.println("REMOVED: " + search);
        }
        return future;
    }
}
