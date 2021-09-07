package nl.inl.blacklab.searches;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;

public class SearchCacheDebug implements SearchCache {

    Map<Search<?>, SearchCacheEntry<? extends SearchResult>> searches = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <R extends SearchResult> SearchCacheEntry<R> getAsync(Search<R> search, boolean allowQueue) {
        SearchCacheEntry<R> result = (SearchCacheEntry<R>) searches.get(search);
        if (result == null) {
            R searchResults;
            try {
                searchResults = search.executeNoQueue();
            } catch (InvalidQuery e) {
                throw new CompletionException(e);
            }
            result = SearchCacheEntry.fromFuture(CompletableFuture.completedFuture(searchResults));
            searches.put(search, result);
            System.out.println("Not found in cache, adding now: " + search);
        } else {
            System.out.println("Found in cache: " + search);
        }
        return result;
    }

    @Override
    public <R extends SearchResult> SearchCacheEntry<R> remove(Search<R> search) {
        System.out.println("Remove from cache: " + search);
        return null;
    }


    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear(boolean terminateRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cleanup() {
        // NOP
    }
}