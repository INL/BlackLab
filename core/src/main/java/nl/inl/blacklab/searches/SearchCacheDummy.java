package nl.inl.blacklab.searches;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import nl.inl.blacklab.search.results.SearchResult;

/** A dummy cache that does nothing. */
public final class SearchCacheDummy implements SearchCache {

    @Override
    public Future<? extends SearchResult> get(Search search, Supplier<? extends SearchResult> supplier) {
        return null; // It's never in cache, and don't add it either
    }

    @Override
    public Future<? extends SearchResult> remove(Search search) {
        // Do nothing.
        return null;
    }
}
