package nl.inl.blacklab.searches;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import nl.inl.blacklab.search.results.SearchResult;

public class SearchCacheDebug implements SearchCache {
    
    Map<Search, CompletableFuture<? extends SearchResult>> searches = new HashMap<>();

    @Override
    public Future<? extends SearchResult> getAsync(Search search, Supplier<? extends SearchResult> supplier) {
        CompletableFuture<? extends SearchResult> result = searches.get(search);
        if (result == null) {
            result = CompletableFuture.supplyAsync(supplier);
            searches.put(search, result);
            System.out.println("Not found in cache, adding now: " + search);
        } else {
            System.out.println("Found in cache: " + search);
        }
        return result;
    }

    @Override
    public Future<? extends SearchResult> remove(Search search) {
        System.out.println("Remove from cache: " + search);
        return null;
    }
    
}