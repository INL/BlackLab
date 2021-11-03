package nl.inl.blacklab.testutil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCacheEntry;

/**
 * Future result for a search operation, executed in a thread.
 *
 * Because searches run in their own thread, we can interrupt long-running
 * searches halfway. Call {@link #cancel(boolean)} with `true` to interrupt
 * a running search.
 *
 * @param <T> search result type
 */
public class FutureSearchResult<T extends SearchResult> extends SearchCacheEntry<T> {

    /** Thread running the search */
    private Thread thread;

    /** Result of the search */
    private T result = null;

    /** True if this search was canceled, false if not */
    private boolean cancelled = false;

    /** Exception thrown by our thread, or null if no exception was thrown */
    private Exception exceptionThrown = null;

    public FutureSearchResult(Search<T> search) {
        this.thread = new Thread(() -> {
            try {
                result = search.executeInternal();
            } catch (Exception e) {
                exceptionThrown = e;
            }
        });
        this.thread.start();
    }

    @Override
    public boolean cancel(boolean interrupt) {
        synchronized (this) {
            if (this.thread == null || !this.thread.isAlive())
                return false; // cannot cancel
            cancelled = true;
            if (interrupt)
                this.thread.interrupt();
        }
        return true;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (this.thread != null) {
                this.thread.join();
                this.thread = null;
            }
        }
        if (exceptionThrown != null)
            throw new ExecutionException(exceptionThrown);
        return result;
    }

    @Override
    public T get(long time, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (this) {
            if (this.thread != null) {
                long ms = unit.toMillis(time);
                this.thread.join(ms);
                if (this.thread.isAlive())
                    throw new TimeoutException("Thread still running after " + ms + "ms");
                this.thread = null;
            }
        }
        if (exceptionThrown != null)
            throw new ExecutionException(exceptionThrown);
        return result;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return !isCancelled() && result != null;
    }

    @Override
    public boolean wasStarted() {
        return true;
    }

    @Override
    public void start() {
        // Never queued, so not implemented
    }

}