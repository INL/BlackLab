package nl.inl.blacklab.server.search;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.util.ThreadPauser;

class NewBlsSearchCache implements SearchCache {
    
    protected Map<Search, Future<? extends SearchResult>> searches = new HashMap<>();
    
    protected boolean trace = false;

    private BlsConfigCacheAndPerformance config;

    public NewBlsSearchCache(BlsConfigCacheAndPerformance config) {
        this.config = config;
        
        // Make sure long operations can be paused.
        ThreadPauser.setEnabled(config.enableThreadPausing());
        
        // (loadmgrthread)
    }
     public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    @Override
    public Future<? extends SearchResult> get(Search search, Supplier<? extends SearchResult> searchTask) {
        Future<? extends SearchResult> future;
        synchronized (searches) {
            future = searches.get(search);
            if (future == null) {
                future = new NewBlsCacheEntry<>(search, searchTask);
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