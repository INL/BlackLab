package nl.inl.blacklab.server.search;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.util.ThreadAborter;

public class BlsCacheEntry<T extends SearchResult> implements Future<T> {

    /** When waiting for the task to complete, poll how often? (ms) */
    private static final int POLLING_TIME_MS = 100;

    /**
     * How long a job remains "young". Young jobs are treated differently than old
     * jobs when it comes to load management, because we want to give new searches a
     * fair chance, but we also want to eventually put demanding searches on the
     * back burner if the system is overloaded.
     */
    public static final int YOUTH_THRESHOLD_SEC = 20;

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

    /** Our thread */
    class SearchTask implements Runnable {

        private boolean fetchAllResults;

        public SearchTask(boolean fetchAllResults) {
            this.fetchAllResults = fetchAllResults;
        }

        /**
         * Run the thread, performing the requested search.
         */
        @Override
        public void run() {
            try {
                boolean isResultsInstance = false;
                try {
                    Supplier<T> resultSupplier = supplier;
                    supplier = null;
                    result = resultSupplier.get();
                    isResultsInstance = result instanceof Results<?>;
                    if (isResultsInstance) {
                        // Make sure our results object can be aborted
                        threadAborter = ((Results<?>)result).threadAborter();
                    }
                } finally {
                    initialSearchDone = true;
                }
                if (fetchAllResults) {
                    if (isResultsInstance) {
                        // Fetch all results from the result object
                        ((Results<?>) result).resultsStats().processedTotal();
                    }
                    if (result instanceof ResultCount) {
                        // Complete the count
                        ((ResultCount) result).processedTotal();
                    }
                }
            } catch (Throwable e) {
                // NOTE: we catch Throwable here (while it's normally good practice to
                //  catch only Exception and derived classes) because we need to know if
                //  our thread crashed or not. The Throwable will be re-thrown by the
                //  main thread, so any non-Exception Throwables will then go uncaught
                //  as they "should".
                exceptionThrown = e;
            } finally {
                fullSearchDoneTime = now();
                fullSearchDone = true;
                future = null;
            }
        }

    }

    /** Unique entry id */
    long id;

    /** Our search */
    private Search<T> search;

    /** Supplier of our result, if the thread hasn't been created yet (cleared by thread) */
    private Supplier<T> supplier;


    // OUTCOMES

    /** Result of the search (set by thread) */
    private T result = null;

    /** Exception thrown by our thread, or null if no exception was thrown (set by thread) */
    private Throwable exceptionThrown = null;

    /** True if this search was canceled, false if not */
    private boolean cancelled = false;


    // TIMING

    /** When was this entry created (ms) */
    private long createTime;

    /** When was this entry last accessed (ms) */
    private long lastAccessTime;

    /** Did the initial search finish, succesfully or otherwise? (set by thread) */
    private boolean initialSearchDone = false;

    /** When did we finish our task? (ms; only valid when finished; set by thread) */
    private long fullSearchDoneTime = 0;

    /** Did our task finish, succesfully or otherwise? (set by thread) */
    private boolean fullSearchDone = false;

    /** Handles aborting search if necessary, and keeping track of search time */
    ThreadAborter threadAborter = null;

    /** Worthiness of this search in the cache, once calculated */
    private long worthiness = 0;

    private Future<?> future;

    /**
     * Construct a cache entry.
     *
     * @param search the search
     * @param supplier the result supplier
     */
    public BlsCacheEntry(Search<T> search) {
        this.search = search;
        this.supplier = search.getSupplier();
        id = getNextEntryId();
        createTime = lastAccessTime = now();
    }

    /**
     * Start performing the task.
     *
     * @param block if true, blocks until the result is available
     */
    public void start(boolean block) {
        SearchTask runnable = new SearchTask(search.fetchAllResults());
        future = search.queryInfo().index().blackLab().searchExecutorService().submit(runnable);
        if (block) {
            try {
                // Wait until result available
                while (!initialSearchDone && !futureDone() && !cancelled) {
                    Thread.sleep(100);
                }
                if (cancelled || futureCancelled())
                    throw new InterruptedSearch("Search was cancelled");
            } catch (InterruptedException e) {
                throw new InterruptedSearch(e);
            }
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
        return cancelled;
    }

    /**
     * Is the initial search finished?
     *
     * This means our result is available, or an error occurred.
     *
     * Note that if the result is available, it does not necessarily
     * mean the results object has e.g. read all its hits. For that,
     * see {@link #isSearchDone()}.
     */
    @Override
    public boolean isDone() {
        return initialSearchDone;
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
        return fullSearchDone ? now() - fullSearchDoneTime : 0;
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
        if (fullSearchDone)
            return fullSearchDoneTime - createTime;
        else return timeSinceCreationMs();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        // Wait until result available
        while (!initialSearchDone && !futureDone() && !cancelled) {
            Thread.sleep(100);
        }
        if (cancelled || futureCancelled())
            throw new InterruptedSearch("Search was cancelled");
        if (exceptionThrown != null)
            throw new ExecutionException(exceptionThrown);
        return result;
    }

    private boolean futureCancelled() {
        return future != null && future.isCancelled();
    }

    private boolean futureDone() {
        return future != null && future.isDone();
    }

    @Override
    public T get(long time, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // Wait until result available
        long ms = unit.toMillis(time);
        while (ms > 0 && !initialSearchDone && !futureDone() && !cancelled) {
            Thread.sleep(POLLING_TIME_MS);
            ms -= POLLING_TIME_MS;
        }
        if (cancelled || futureCancelled())
            throw new InterruptedSearch("Search was cancelled");
        if (exceptionThrown != null)
            throw new ExecutionException(exceptionThrown);
        if (!initialSearchDone)
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
            // (the more recently used, the worthier)

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

        } else if (timeUserWaitedMs() / 1000 > YOUTH_THRESHOLD_SEC) {
            // 10000 ... 19999: search has been running for a long time and is counting hits
            // 20000 ... 29999: search has been running for a long time and is retrieving hits
            // (younger searches are considered worthier)
            boolean isCount = search instanceof SearchCount;
            worthiness = Math.max(10000, 19999 - timeUserWaitedMs() / 1000) + (isCount ? 0 : 10000);
        } else {
            // 30000 ... 39999: search hasn't been running for very long yet
            // (the more recent, the worthier)
            worthiness = Math.max(30000, 39999 - timeUserWaitedMs() / 1000);
        }
    }

    @Override
    public boolean cancel(boolean interrupt) {
        if (initialSearchDone)
            return false; // cannot cancel
        cancelled = true;
        Future<?> theFuture = future; // avoid locking
        if (interrupt && theFuture != null) {
            theFuture.cancel(interrupt);
            future = null;
        }
        return true;
    }

    /**
     * Is this search done, even fetching all hits (if that was requested)
     *
     * This method exists because our search operation can sometimes continue even after
     * isDone() starts returning true. Total counts work this way, because we want to keep
     * track of the count while it's happening, so we need access to the result object
     * before the count is complete.
     *
     * @return true if the search is fully complete (or threw and exception)
     */
    public boolean isSearchDone() {
        return fullSearchDone;
    }

    /**
     * Cancel the search, including fetching all hits (if that's being done).
     *
     * This method exists because the Future contract states that you cannot cancel
     * a Future after isDone() starts returning true. But our "total counts" are a
     * special case, where the thread keeps running to fetch all hits to calculate the
     * total, even when the result object is available (because we want to keep track
     * of the count as it goes).
     *
     * To circumvent this, we implement our own method that's not bound by the contract.
     *
     * This method will always interrupt the operation if it's running.
     *
     * It will only affect the cancelled status of the Future if the Future hadn't
     * completed yet.
     *
     * @return true if the search was cancelled, false if it could not be cancelled (because it wasn't running anymore)
     */
    public boolean cancelSearch() {
        if (!initialSearchDone) {
            // Regular situation; use regular cancel method.
            return cancel(true);
        }
        if (fullSearchDone)
            return false; // cannot cancel
        Future<?> theFuture = future; // avoid locking
        if (theFuture != null) {
            theFuture.cancel(true);
            future = null;
        }
        return true;
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
        if (isSearchDone())
            return "finished";
        if (isDone())
            return "counting";
        return "running";
    }

    public void dataStream(DataStream ds, boolean debugInfo) {
        boolean isCount = search instanceof SearchCount;
        ds.startMap()
                .entry("id", id)
                .entry("class", search.getClass().getSimpleName())
                .entry("jobDesc", search.toString())
                .startEntry("stats")
                .startMap()
                .entry("type", isCount ? "count" : "search")
                .entry("status", status())
                .entry("cancelled", cancelled)
                .entry("futureStatus", futureStatus())
                .entry("exceptionThrown", exceptionThrown == null ? "" : exceptionThrown.getClass().getSimpleName())
                .entry("sizeBytes", numberOfStoredHits() * BlsCache.SIZE_OF_HIT)
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
        if (future == null)
            return "null";
        if (future.isCancelled())
            return "cancelled";
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

}