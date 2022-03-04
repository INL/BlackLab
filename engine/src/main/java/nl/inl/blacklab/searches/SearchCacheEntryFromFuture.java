package nl.inl.blacklab.searches;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.SearchResult;

/**
 * A SearchCacheEntry from a Future.
 *
 * This cache entry is never queued.
 *
 * @param <R> the type of search result this search will yield
 */
public class SearchCacheEntryFromFuture<R extends SearchResult> extends SearchCacheEntry<R> {

    private final Search<R> search;

    final Future<R> future;

    /** A peek at the future result, or null if not available. */
    private R peekValue;

    public SearchCacheEntryFromFuture(Future<R> future, Search<R> search) {
        this.future = future;
        this.search = search;
        peekValue = search.peekObject(this);
    }

    @Override
    public boolean cancel(boolean interrupt) {
        return future.cancel(interrupt);
    }

    @Override
    public R get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public R get(long arg0, TimeUnit arg1) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(arg0, arg1);
    }

    @Override
    public R peek() {
        if (isCancelled())
            throw new InterruptedSearch();
        return peekValue;
    }

    /**
     * Was this future cancelled?
     *
     * If it was, {@link #isDone()} will also return true.
     *
     * @return true if it was, false if not
     */
    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * Is this future done (cancelled or completed)?
     *
     * @return true if it is, false if not
     */
    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public boolean wasStarted() {
        return true;
    }

    @Override
    public void start() {
        // never queued, so not implemented
    }
}
