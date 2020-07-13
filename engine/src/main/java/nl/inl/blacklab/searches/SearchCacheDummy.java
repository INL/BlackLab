package nl.inl.blacklab.searches;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;

/** A dummy cache that does nothing. */
public final class SearchCacheDummy implements SearchCache {

    @Override
    public <R extends SearchResult> Future<R> getAsync(Search<R> search, Supplier<R> supplier) {
        try {
            return ConcurrentUtils.constantFuture(search.executeInternal()); // It's never in cache, and don't add it either
        } catch (InvalidQuery e) {
            throw BlackLabRuntimeException.wrap(e);
        }
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
