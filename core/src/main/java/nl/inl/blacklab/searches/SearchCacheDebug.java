package nl.inl.blacklab.searches;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import nl.inl.blacklab.search.results.SearchResult;

public class SearchCacheDebug implements SearchCache {
    
    Map<Search, CompletableFuture<? extends SearchResult>> searches = new HashMap<>();

    @Override
    public CompletableFuture<? extends SearchResult> get(Search search,
            CompletableFuture<? extends SearchResult> future) {
        CompletableFuture<? extends SearchResult> result = searches.get(search);
        if (result != null) {
            System.out.println("Found in cache: " + search);
        } else {
            searches.put(search, future);
            System.out.println("Not found in cache, adding now: " + search);
        }
        return result;
    }

    @Override
    public void remove(Search search) {
        System.out.println("Remove from cache: " + search);
    }
    
}