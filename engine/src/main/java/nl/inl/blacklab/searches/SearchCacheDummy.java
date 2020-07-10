package nl.inl.blacklab.searches;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;

/** A dummy cache that does nothing. */
public final class SearchCacheDummy implements SearchCache {

    @Override
    public <R extends SearchResult> Future<R> getAsync(Search<R> search, Supplier<R> supplier) {
        return null; // It's never in cache, and don't add it either
    }

    @Override
    public <R extends SearchResult> Future<R> remove(Search<R> search) {
        // Do nothing.
        return null;
    }

    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        // OK
    }
    
    @Override
    public void clear(boolean terminateRunning) {
        // OK
    }

    @Override
    public void cleanup() {
        // NOP
    }
}
