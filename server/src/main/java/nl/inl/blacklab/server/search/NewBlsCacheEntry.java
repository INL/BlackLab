package nl.inl.blacklab.server.search;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.util.ThreadPauser;

class NewBlsCacheEntry<T extends SearchResult> implements Future<T> {
    
    /**
     * Funcionality related to pausing the result object for this search.
     * 
     * TODO: maybe move this into ThreadPauser?
     */
    static class Pausing {

        /** The result object's threadPauser object, if any */
        private ThreadPauser threadPauser = null;
        
        /** Remember if we're supposed to be paused for when we get a ThreadPauser */
        private boolean shouldBePaused = false;
    
        private long setToPausedTime = 0;
    
        private long setToRunningTime;
    
        private long pausedTime = 0;
        
        public Pausing() {
            setToRunningTime = now();
        }
        
        public void setThreadPauser(ThreadPauser threadPauser) {
            this.threadPauser = threadPauser;
            if (shouldBePaused)
                threadPauser.pause(true);  // we were set to paused before we had the ThreadPauser
        }
        
        public ThreadPauser threadPauser() {
            return threadPauser;
        }
        
        public boolean isPaused() {
            return threadPauser() == null ? shouldBePaused : threadPauser.isPaused();
        }
        
        public void setPaused(boolean paused) {
            this.shouldBePaused = paused;
            if (paused)
                setToPausedTime = now();
            else {
                // Unpause. Keep track of how long we've been paused total.
                pausedTime += now () - setToPausedTime;
                setToRunningTime = now();
            }
            if (threadPauser() != null)
                threadPauser.pause(paused);
        }
    
        /**
         * How long has this job been paused for currently?
         *
         * This does not include any previous pauses.
         *
         * @return number of ms since the job was paused, or 0 if not paused
         */
        public long currentPauseLength() {
            if (!isPaused())
                return 0;
            return now() - setToPausedTime;
        }
    
        /**
         * How long has this job been running currently?
         *
         * This does not include any previous running phases.
         *
         * @return number of ms since the job was set to running, or 0 if not running
         */
        public double currentRunPhaseLength() {
            if (isPaused())
                return 0;
            return (System.currentTimeMillis() - setToRunningTime) / 1000.0;
        }
    
        /**
         * How long has this job been paused in total?
         *
         * @return total number of ms the job has been paused
         */
        public long pausedTotal() {
            if (!isPaused())
                return pausedTime;
            return pausedTime + now() - setToPausedTime;
        }
    }
    
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
    private Search search;

    /** Thread running the search */
    private Thread thread;
    
    Pausing pausing = new Pausing();


    // OUTCOMES

    /** Result of the search */
    private T result = null;

    /** Exception thrown by our thread, or null if no exception was thrown */
    private Exception exceptionThrown = null;
    
    /** True if this search was canceled, false if not */
    private boolean cancelled = false;
    
    
    // TIMING

    /** When was this entry created (ms) */
    private long createTime;
    
    /** When was this entry last accessed (ms) */
    private long lastAccessTime;
    
    /** When was did the thread finish (ms; only valid when finished) */
    private long threadFinishTime = 0;
    
    /** Did the thread finish, succesfully or otherwise? */
    private boolean threadFinished = false;

    private Supplier<T> supplier;
    
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
     * @throws InterruptedException 
     */
    public void start(boolean block) throws InterruptedException {
        thread = new Thread(() -> {
            performTask(supplier);
        });
        thread.start();
        if (block)
            thread.join();
    }

    private void performTask(Supplier<T> supplier) {
        try {
            result = supplier.get();
            if (result instanceof Results<?>) {
                pausing.setThreadPauser(((Results<?>)result).threadPauser());
            }
        } catch (Exception e) {
            exceptionThrown = e;
        } finally {
            threadFinishTime = now();
            threadFinished = true;
        }
    }

    public long id() {
        return id;
    }

    public Search search() {
        return search;
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

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return threadFinished;
    }
    
    public void updateLastAccess() {
        this.lastAccessTime = now();
    }
    
    public long timeSinceCreation() {
        return now() - createTime;
    }

    public long timeSinceLastAccess() {
        return now() - lastAccessTime;
    }

    public long timeSinceFinished() {
        if (!threadFinished)
            return 0;
        return now() - threadFinishTime;
    }
    
    /**
     * How long the user has waited for this job.
     *
     * For finished searches, this is from the start time to the finish time. For
     * other searches, from the start time until now.
     *
     * @return execution time in ms
     */
    public long userWaitTime() {
        if (threadFinished)
            return threadFinishTime - createTime;
        return timeSinceCreation();
    }
    
    /**
     * How long has this job actually been running in total?
     *
     * Running time is the total time minus the paused time.
     *
     * @return total number of ms the job has actually been running
     */
    public long totalRunningTime() {
        return userWaitTime() - pausing.pausedTotal();
    }
    
}