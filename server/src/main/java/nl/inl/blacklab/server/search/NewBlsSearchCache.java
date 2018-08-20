package nl.inl.blacklab.server.search;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.util.ThreadPauserImpl;

public class NewBlsSearchCache implements SearchCache {
    
    private static final Logger logger = LogManager.getLogger(NewBlsSearchCache.class);
    
    public static final boolean ENABLE_NEW_CACHE = false;

    protected Map<Search, NewBlsCacheEntry<? extends SearchResult>> searches = new HashMap<>();
    
    protected boolean trace = true;

    private BlsConfigCacheAndPerformance config;

    public NewBlsSearchCache(BlsConfigCacheAndPerformance config) {
        this.config = config;
        
        // Make sure long operations can be paused.
        ThreadPauserImpl.setEnabled(config.enableThreadPausing());
        
        // (loadmgrthread)
    }
    
    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    @Override
    public NewBlsCacheEntry<? extends SearchResult> getAsync(Search search, Supplier<? extends SearchResult> searchTask) {
        return getFromCache(search, searchTask, false);
    }

    @Override
    public SearchResult get(Search search, Supplier<? extends SearchResult> searchTask) throws ExecutionException {
        NewBlsCacheEntry<? extends SearchResult> entry = getFromCache(search, searchTask, true);
        try {
            return entry.get();
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }

    private NewBlsCacheEntry<? extends SearchResult> getFromCache(Search search,
            Supplier<? extends SearchResult> searchTask, boolean block) {
        NewBlsCacheEntry<? extends SearchResult> future;
        boolean created = false;
        synchronized (searches) {
            future = searches.get(search);
            if (future == null) {
                future = new NewBlsCacheEntry<>(search, searchTask);
                created = true;
                searches.put(search, future);
                if (!block)
                    future.start(false);
            }
        }
        if (created) {
            if (trace)
                logger.info("-- ADDED: " + search);
            if (block)
                future.start(true);
        } else {
            if (trace)
                logger.info("-- FOUND: " + search);
            future.updateLastAccess();
        }
        return future;
    }
    
    @Override
    public NewBlsCacheEntry<? extends SearchResult> remove(Search search) {
        NewBlsCacheEntry<? extends SearchResult> future;
        synchronized (searches) {
            future = searches.remove(search);
            if (trace)
                logger.info("-- REMOVED: " + search);
        }
        return future;
    }

   
}