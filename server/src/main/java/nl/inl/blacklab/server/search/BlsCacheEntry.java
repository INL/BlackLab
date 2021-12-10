package nl.inl.blacklab.server.search;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.datastream.DataStream;
import org.apache.logging.log4j.ThreadContext;

public class BlsCacheEntry<T extends SearchResult> extends SearchCacheEntry<T> {

    /** When waiting for the task to complete, poll how often? (ms) */
    static final int POLLING_TIME_MS = 100;

    /** id for the next job started */
    private static Long nextEntryId = 0L;

    private static long now() {
        return System.currentTimeMillis();
    }

    public static long getNextEntryId() {
        Long n;
        synchronized(nextEntryId) {
            n = nextEntryId;
            nextEntryId++;
        }
        return n;
    }

    /** Unique entry id */
    long id;

    /** Our search */
    private Search<T> search;


    // OUTCOMES

    /**
     * Future result of the search task.
     * Note that the actual result of this future is never retrieved,
     * because the thread sets our result instance variable directly.
     */
    private Future<?> future = null;

    /** Result of the search (set directly by thread) */
    private T result = null;

    /** Exception thrown by our thread, or null if no exception was thrown (set by thread) */
    private Throwable exceptionThrown = null;

    /** If the search couldn't complete or was aborted, this may contain the exact reason why, e.g.
     *  "Search aborted because it took longer than the maximum of 5 minutes. This may be a very demanding
     *   search, or the server may be under heavy load. Please try again later."
     */
    private String reason = "";


    // TIMING

    /** When was this entry created (ms) */
    private long createTime;

    /** When was this entry last accessed (ms) */
    private long lastAccessTime;

    /** When did we finish or cancel our task? (ms; set by thread) */
    private long doneTime = 0;

    /** Worthiness of this search in the cache, once calculated */
    private long worthiness = 0;

    /** Has this search been started yet, or has it been queued because load is too high?
     *
     * All searches start with this set to false. When it is decided that the search can be
     * started (because another search task needs its results, or because load is low enough for
     * "new" searches), start() is called and this is set to true.
     */
    private boolean started = false;

    /** Was this cancelled? (future is set to null in that case, to free the memory, so we need this status) */
    private boolean cancelled = false;

    /**
     * Construct a cache entry.
     *
     * @param search the search
     */
    public BlsCacheEntry(Search<T> search) {
        this.search = search;
        id = getNextEntryId();
        createTime = lastAccessTime = now();
    }

    /**
     * Start performing the task.
     *
     * @param block if true, blocks until the result is available
     */
    @Override
    public void start() {
        if (future != null)
            throw new RuntimeException("Search already started");
        started = true;
        final String requestId = ThreadContext.get("requestId");
        future = search.queryInfo().index().blackLab().searchExecutorService().submit(() -> {
            ThreadContext.put("requestId", requestId);
            executeSearch();
        });
    }

    /** Perform the requested search.
     *
     * {@link #start(boolean)} submits a Runnable to the search executor service that calls this.
     */
    public void executeSearch() {
        try {
            result = search.executeInternal();
        } catch (Throwable e) {

            if (e instanceof InterruptedSearch) {
                // Inject ourselves into the exception object, so
                // the code that eventually catches it can access us,
                // e.g. to find out the exact reason a search was cancelled
                ((InterruptedSearch)e).setCacheEntry(this);
            }

            // NOTE: we catch Throwable here (while it's normally good practice to
            //  catch only Exception and derived classes) because we need to know if
            //  our thread crashed or not. The Throwable will be re-thrown by the
            //  main thread, so any non-Exception Throwables will then go uncaught
            //  as they "should".
            exceptionThrown = e;
        } finally {
            doneTime = now();
        }
    }

    public long id() {
        return id;
    }

    public Search<T> search() {
        return search;
    }

    public long worthiness() {
        return worthiness;
    }

    public Throwable exceptionThrown() {
        return exceptionThrown;
    }

    public String exceptionStacktrace() {
        if (exceptionThrown == null)
            return "";
        StringWriter out = new StringWriter();
        exceptionThrown.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    @Override
    public boolean isCancelled() {
        return cancelled || future != null && future.isCancelled();
    }

    /**
     * Is the initial search finished?
     *
     * This means our result is available, or an error occurred.
     *
     * Note that if the result is available, it does not necessarily
     * mean the results object has e.g. read all its hits.
     */
    @Override
    public boolean isDone() {
        return future != null && future.isDone() || cancelled;
    }

    /**
     * How long ago was this search created?
     *
     * @return time since creation (ms)
     */
    public long timeSinceCreationMs() {
        return now() - createTime;
    }

    /**
     * How long ago was this search last accessed?
     *
     * Access time is updated whenever the search is retrieved from the cache.
     *
     * @return time since last access (ms)
     */
    public long timeSinceLastAccessMs() {
        return now() - lastAccessTime;
    }

    /**
     * How long ago did the search finish?
     *
     * A search is considered finished as soon as its results object
     * is available (even though it hasn't actually read its hits yet).
     *
     * @return time since search finished (ms)
     */
    public long timeSinceFinishedMs() {
        return isDone() ? now() - doneTime : 0;
    }

    /**
     * How long has this search been unused?
     *
     * Unused time is defined as zero if the search is running, and the time since last access
     * if the search is finished.
     *
     * @return the unused time (ms)
     */
    public long timeUnusedMs() {
        return isDone() ? now() - lastAccessTime : 0;
    }

    /**
     * How long did the user have to wait for the results?
     *
     * For finished searches, this is from the start time to the finish time. For
     * other searches, from the start time until now.
     *
     * @return user wait time (ms)
     */
    public long timeUserWaitedMs() {
        if (isDone())
            return doneTime - createTime;
        else return timeSinceCreationMs();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        try {
            return get(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public T get(long time, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // Wait until result available
        long ms = unit.toMillis(time);
        while (ms > 0 && !isDone() && !isCancelled()) {
            Thread.sleep(POLLING_TIME_MS);
            ms -= POLLING_TIME_MS;
        }
        if (isCancelled()) {
            InterruptedSearch interruptedSearch = new InterruptedSearch("Search was cancelled");
            interruptedSearch.setCacheEntry(this);
            throw interruptedSearch;
        }
        if (exceptionThrown != null)
            throw new ExecutionException(exceptionThrown);
        if (!isDone())
            throw new TimeoutException("Result still not available after " + ms + "ms");
        return result;
    }

    /**
     * Set the last accessed time to now.
     */
    public void updateLastAccess() {
        this.lastAccessTime = now();
    }

    /**
     * Calculate 'worthiness'.
     *
     * You should call calculateWorthiness() on the objects you're going to
     * compare before using the worthiness comparator. This makes sure no changes
     * in worthiness can occur during the sorting process. This is required by the
     * Comparable interface and TimSort complains if the contract is violated by
     * an object changing while sorting.
     *
     * 'Worthiness' is a measure indicating how important a job is, and determines
     * what jobs get the CPU and what jobs are aborted. It also determines
     * what finished jobs are removed from the cache.
     */
    public void calculateWorthiness() {
        if (isDone()) {
            // 0 ... 9999 : search is finished
            // - the more recently used, the worthier
            // - the longer it took to execute, the worthier
            // - the smaller, the worthier

            // Size score from 1-100; 1M per unit, so 100 corresponds to 100M or larger
            int sizeScore = Math.max(1, Math.min(100, numberOfStoredHits() * BlsCache.SIZE_OF_HIT / 1000000));

            // Run time score from 1-10000; 0.03s per unit, so 10000 corresponds to 5 minutes or longer
            long runTimeScore = Math.max(1, Math.min(10000, timeUserWaitedMs() * 10 / 300));

            // Last accessed score from 1-100: 3s per unit, so 100 corresponds to 5 minutes or longer
            long lastAccessScore = Math.max(1, Math.min(100, timeSinceLastAccessMs() / 3000));

            if (timeSinceLastAccessMs() < 60000) {
                // For the first minute of the search, pretend it's a search that took really long
                // and is really small, so it won't be eliminated from the cache right away.
                worthiness = 10000 / lastAccessScore;
            } else {
                // After a minute, start taking into account the size of the results set and
                // and the time it will take to recreate it.
                worthiness = (long)((double)runTimeScore / lastAccessScore / sizeScore);
            }

        } else {
            // 10000 ... 19999: search has been running for a long time and is counting hits
            // 20000 ... 29999: search has been running for a long time and is retrieving hits
            // - the younger, the worthier
            boolean isCount = search instanceof SearchCount;
            worthiness = Math.max(10000, 19999 - timeUserWaitedMs() / 1000) + (isCount ? 0 : 10000);
        }
    }

    /**
     * Cancel the search, including fetching all hits (if that's being done).
     *
     * @param interrupt
     * @return true if the search was cancelled, false if it could not be cancelled (because it wasn't running anymore)
     */
    @Override
    public boolean cancel(boolean interrupt) {
        Future<?> theFuture = future; // avoid locking
        boolean result = false;
        if (theFuture != null && !theFuture.isDone()) {
            cancelled = true;
            result = theFuture.cancel(interrupt);

            // Ensure memory can be freed
            future = null;
            this.result = null;

            doneTime = now();
        }
        return result;
    }

    public boolean threwException() {
        return exceptionThrown != null;
    }

    public int numberOfStoredHits() {
        if (result == null)
            return 0;
        return result.numberOfResultObjects();
    }

    public String status() {
        if (!wasStarted())
            return "queued";
        if (isCancelled())
            return "cancelled";
        if (isDone())
            return "finished";
        return "running";
    }

    public void dataStream(DataStream ds, boolean debugInfo) {
        boolean isCount = search instanceof SearchCount;
        ds.startMap()
                //.entry("id", id)
                .entry("class", search.getClass().getSimpleName())
                .entry("jobDesc", search.toString())
                .startEntry("stats")
                .startMap()
                .entry("type", isCount ? "count" : "search")
                .entry("status", status());
                //.entry("cancelled", isCancelled());
                //.entry("futureStatus", futureStatus())
        if (exceptionThrown != null) {
             ds.entry("exceptionThrown", exceptionThrown.getClass().getSimpleName());
        }
        if (!StringUtils.isEmpty(reason))
            ds.entry("cancelReason", reason);
        ds.entry("numberOfStoredHits", numberOfStoredHits())
                .entry("userWaitTime", timeUserWaitedMs() / 1000.0)
                .entry("notAccessedFor", timeSinceLastAccessMs() / 1000.0)
                //.entry("createdBy", shortUserId())
                //.entry("refsToJob", refsToJob - 1) // (- 1 because the cache always references it)
                //.entry("waitingForJobs", waitingFor.size())
                //.entry("url", jobDesc.getUrl())
                .endMap()
                .endEntry();
        if (debugInfo) {
            ds.startEntry("debugInfo");
            dataStreamDebugInfo(ds, this);
            ds.endEntry();
        }
//        dataStreamSubclassEntries(ds);
//        if (inputJob != null) {
//            ds.startEntry("inputJob").startMap();
//            ds.entry("type", inputJob.getClass().getName());
//            Hits hits = null;
//            if (inputJob instanceof JobWithHits) {
//                hits = ((JobWithHits) inputJob).getHits();
//            }
//            ds.entry("hasHitsObject", hits != null);
//            if (hits != null) {
//                ResultsStats hitsStats = hits.hitsStats();
//                ds.entry("hitsObjId", hits.resultsObjId())
//                        .entry("retrievedSoFar", hitsStats.processedSoFar())
//                        .entry("doneFetchingHits", hitsStats.done());
//            }
//            ds.endMap().endEntry();
//        }
        ds.endMap();
    }

    public String futureStatus() {
        if (cancelled || future.isCancelled())
            return "cancelled";
        if (future == null)
            return "future==null";
        if (future.isDone()) {
            try {
                future.get();
            } catch (InterruptedException e) {
                return "interruptedEx" + e.getMessage();
            } catch (ExecutionException e) {
                return "executionEx: " + e.getMessage();
            }
            return "done";
        }
        return "running";
    }

    private static void dataStreamDebugInfo(DataStream ds, BlsCacheEntry<?> entry) {
        ds.startMap();
        // More information about job state
        ds.entry("timeSinceCreation", entry.timeSinceCreationMs())
                .entry("timeSinceFinished", entry.timeSinceFinishedMs())
                .entry("timeSinceLastAccess", entry.timeSinceLastAccessMs())
                .entry("searchCancelled", entry.isCancelled())
                .startEntry("thrownException")
                .startMap();
        // Information about thrown exception, if any
        Throwable exceptionThrown = entry.exceptionThrown();
        if (exceptionThrown != null) {
            PrintWriter st = new PrintWriter(new StringWriter());
            exceptionThrown.printStackTrace(st);
            ds
                    .entry("class", exceptionThrown.getClass().getName())
                    .entry("message", exceptionThrown.getMessage())
                    .entry("stackTrace", st.toString());
        }
        ds.endMap()
                .endEntry()
                .startEntry("searchThread")
                .startMap();
        // Information about thread object, if any
//        Thread thread = entry.thread();
//        if (thread != null) {
//            StackTraceElement[] stackTrace = thread.getStackTrace();
//            StringBuilder stackTraceStr = new StringBuilder();
//            for (StackTraceElement element : stackTrace) {
//                stackTraceStr.append(element.toString()).append("\n");
//            }
//            ds
//                    .entry("name", thread.getName())
//                    .entry("osPriority", thread.getPriority())
//                    .entry("isAlive", thread.isAlive())
//                    .entry("isDaemon", thread.isDaemon())
//                    .entry("isInterrupted", thread.isInterrupted())
//                    .entry("state", thread.getState().toString())
//                    .entry("currentlyExecuting", stackTraceStr.toString());
//        }
        ds.endMap()
                .endEntry()
                .endMap();
    }

    @Override
    public String toString() {
        return "BlsCacheEntry(" + search + ", " + status() + ")";
    }

    @Override
    public boolean wasStarted() {
        return started;
    }

    /**
     * Set reason the search was cancelled or could not complete.
     *
     * @param reason descriptive reason
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * Get reason the search was cancelled or could not complete.
     *
     * @return descriptive reason or empty string
     */
    @Override
    public String getReason() {
        return reason;
    }

}
