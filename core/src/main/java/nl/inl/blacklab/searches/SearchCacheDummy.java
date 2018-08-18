package nl.inl.blacklab.searches;

import java.util.concurrent.CompletableFuture;

import nl.inl.blacklab.search.results.SearchResult;

/** A dummy cache that does nothing. */
public final class SearchCacheDummy implements SearchCache {

    @Override
    public CompletableFuture<? extends SearchResult> get(Search search,
            CompletableFuture<? extends SearchResult> future) {
        return null; // It's never in cache, and don't add it either
    }

    @Override
    public void remove(Search search) {
        // Do nothing.
    }
}
