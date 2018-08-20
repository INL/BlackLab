package nl.inl.blacklab.server.search;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.jobs.Job;
import nl.inl.util.ThreadPauser;

class NewBlsCacheEntry<T extends SearchResult> implements Future<T> {
    
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
    class NewBlsSearchThread extends Thread implements UncaughtExceptionHandler {
        
        public NewBlsSearchThread() {
            setUncaughtExceptionHandler(this);
        }
        
        /**
         * Run the thread, performing the requested search.
         */
        @Override
        public void run() {
            try {
                Supplier<T> resultSupplier = supplier;
                supplier = null;
                result = resultSupplier.get();
                if (result instanceof Results<?>) {
                    // Make sure our results object can be paused
                    pausing.setThreadPauser(((Results<?>)result).threadPauser());
                }
            } catch (Throwable e) {
                // NOTE: we catch Throwable here (while it's normally good practice to
                //  catch only Exception and derived classes) because we need to know if
                //  our thread crashed or not. The Throwable will be re-thrown by the
                //  main thread, so any non-Exception Throwables will then go uncaught
                //  as they "should".

                // We've also set an UncaughtExceptionHandler (the thread object itself)
                // which does the same thing, because apparently some exceptions can occur
                // outside the run() method or aren't caught here for some other reason).
                // Even then, some low-level ones (like OutOfMemoryError) seem to slip by.
                exceptionThrown = e;
            } finally {
                threadFinishTime = now();
                threadFinished = true;
            }
        }

        @Override
        public void uncaughtException(Thread thread, Throwable e) {
            exceptionThrown = e;
        }
        
    }
    
    /** Unique entry id */
    long id;
    
    /** Our search */
    private Search search;

    /** Supplier of our result, if the thread hasn't been created yet (cleared by thread) */
    private Supplier<T> supplier;
    
    /** Thread running the search */
    private NewBlsSearchThread thread;
    

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
    
    /** When was did the thread finish (ms; only valid when finished; set by thread) */
    private long threadFinishTime = 0;
    
    /** Did the thread finish, succesfully or otherwise? (set by thread) */
    private boolean threadFinished = false;

    /** Handles pausing the results object, and keeping track of pause time */
    ThreadPauserProxy pausing = new ThreadPauserProxy();

    /** Worthiness of this search in the cache, once calculated */
    private long worthiness = 0;

    /**
     * Construct a cache entry.
     * 
     * @param search the search
     * @param supplier the result supplier
     * @param block if true, executes task in current thread and blocks until the
     *     result is available. If false, starts a new thread and returns right away.
     */
    public NewBlsCacheEntry(Search search, Supplier<T> supplier) {
        this.search = search;
        this.supplier = supplier;
        id = getNextEntryId();
        createTime = lastAccessTime = now();
    }
    
    /**
     * Start performing the task.
     * 
     * @param block if true, blocks until the task is complete
     */
    public void start(boolean block) {
        thread = new NewBlsSearchThread();
        thread.start();
        if (block) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new InterruptedSearch(e);
            }
        }
    }

    public long id() {
        return id;
    }

    public Search search() {
        return search;
    }

    public long worthiness() {
        return worthiness;
    }

    public ThreadPauser threadPauser() {
        return pausing;
    }

    public Thread thread() {
        return thread;
    }

    public Throwable exceptionThrown() {
        return exceptionThrown;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Has the search finished?
     * 
     * This means the results object is available. It does not
     * necessarily mean the results object has e.g. read all its hits.
     */
    @Override
    public boolean isDone() {
        return threadFinished;
    }

    /**
     * How long ago was this search created?
     * 
     * @return time since creation (ms)
     */
    public long timeSinceCreation() {
        return now() - createTime;
    }

    /**
     * How long ago was this search last accessed?
     * 
     * Access time is updated whenever the search is retrieved from the cache.
     * 
     * @return time since last access (ms)
     */
    public long timeSinceLastAccess() {
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
    public long timeSinceFinished() {
        if (!threadFinished)
            return 0;
        return now() - threadFinishTime;
    }

    /**
     * How long has this search been unused?
     * 
     * Unused time is defined as zero if the search is running, and the time since last access
     * if the search is finished.
     *
     * @return the unused time (ms)
     */
    public long timeUnused() {
        if (isDone())
            return now() - lastAccessTime;
        return 0;
    }

    /**
     * How long did the user have to wait for the results?
     *
     * For finished searches, this is from the start time to the finish time. For
     * other searches, from the start time until now.
     *
     * @return user wait time (ms)
     */
    public long timeUserWaited() {
        if (threadFinished)
            return threadFinishTime - createTime;
        return timeSinceCreation();
    }

    /**
     * How long has this job actually been running in total?
     *
     * Running time is the total time minus the paused time.
     *
     * @return how long the search has actually run (ms)
     */
    public long timeRunning() {
        return timeUserWaited() - pausing.pausedTotal();
    }

    /**
     * How long has this job been paused in total?
     *
     * @return how long the search has been paused (ms)
     */
    public long timePaused() {
        return pausing.pausedTotal();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            try {
                if (this.thread != null) {
                    this.thread.join();
                }
            } finally {
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
            try {
                if (this.thread != null) {
                    long ms = unit.toMillis(time);
                    this.thread.join(ms);
                    if (this.thread.isAlive())
                        throw new TimeoutException("Thread still running after " + ms + "ms");
                }
            } finally {
                this.thread = null;
            }
        }
        if (exceptionThrown != null)
            throw new ExecutionException(exceptionThrown);
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
     * what jobs get the CPU and what jobs are paused or aborted. It also determines
     * what finished jobs are removed from the cache.
     */
    public void calculateWorthiness() {
        if (isDone()) {
            // 0 ... 9999 : search is finished
            // (the more recently used, the worthier)
            worthiness = Math.max(0, 9999 - timeSinceLastAccess());
        } else if (timeRunning() > Job.YOUTH_THRESHOLD_SEC) {
            // 10000 ... 19999: search has been running for a long time and is counting hits
            // 20000 ... 29999: search has been running for a long time and is retrieving hits
            // (younger searches are considered worthier)
            boolean isCount = search instanceof SearchCount;
            worthiness = Math.max(10000, 19999 - timeRunning()) + (isCount ? 0 : 10000);
        } else {
            long runtime = pausing.currentRunPhaseLength();
            boolean justStartedRunning = runtime > Job.ALMOST_ZERO && runtime < Job.RUN_PAUSE_PHASE_JUST_STARTED;
            long pause = pausing.currentPauseLength();
            boolean justPaused = pause > Job.ALMOST_ZERO && pause < Job.RUN_PAUSE_PHASE_JUST_STARTED;
            if (!justPaused && !justStartedRunning) {
                // 30000 ... 39999: search has been running for a short time
                // (older searches are considered worthier, to give searches just started a fair chance of completing)
                worthiness = Math.min(39999, 30000 + timeRunning());
            } else if (justPaused) {
                // 40000 ... 49999: search was just paused
                // (the longer ago, the worthier)
                worthiness = Math.min(49999, 40000 + pause);
            } else {
                // 50000 ... 59999: search was just resumed
                // (the more recent, the worthier)
                worthiness = Math.max(50000, 59999 - runtime);
            }
        }
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
    
}