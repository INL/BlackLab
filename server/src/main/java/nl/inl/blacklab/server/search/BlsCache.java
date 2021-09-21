package nl.inl.blacklab.server.search;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.ServerOverloaded;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.config.BLSConfigCache;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.logging.LogDatabase;
import nl.inl.blacklab.server.logging.LogDatabaseDummy;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.blacklab.server.util.MemoryUtil;

public class BlsCache implements SearchCache {

    private static final Logger logger = LogManager.getLogger(BlsCache.class);

    /** Milliseconds per minute */
    private static final int LOG_CACHE_STATE_INTERVAL_SEC = 60;

    private static final int LOG_CACHE_SNAPSHOT_INTERVAL_SEC = 60 * 5;

    /** Bytes in one megabyte */
    private static final long ONE_MB_BYTES = 1_000_000;

    /** Bytes in one gigabyte */
    private static final long ONE_GB_BYTES = ONE_MB_BYTES * 1_000;

    /** Very rough measure of how large result objects are, based on a Hit (3 ints + 12 bytes object overhead) */
    public static final int SIZE_OF_HIT = 24;

    /**
     * A thread that regularly calls cleanUpSearches() to
     * ensure that cache cleanup continues even if no new requests are coming in.
     */
    class CleanupSearchesThread extends Thread implements UncaughtExceptionHandler {

        private static final int CLEAN_UP_CACHE_INTERVAL_MS = 500;

        /** Construct the load manager thread object. */
        public CleanupSearchesThread() {
            super("CleanupSearches");
            setUncaughtExceptionHandler(this);
        }

        /**
         * Run the thread, performing the requested search.
         */
        @Override
        public void run() {
            while (!interrupted()) {
                try {
                    Thread.sleep(CLEAN_UP_CACHE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    logger.info("CleanupSearchesThread interrupted");
                    return;
                }

                updateCache();
            }
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.error("CleanupSearchesThread threw an exception!");
            e.printStackTrace();
        }

    }

    private BLSConfigCache config;

    private int maxConcurrentSearches;

    /** Abort an abandoned count after how much time? (s) */
    private int abandonedCountAbortTimeSec;

    protected Map<Search<?>, BlsCacheEntry<? extends SearchResult>> searches = new HashMap<>();

    protected boolean trace = false;

    private boolean cacheDisabled;

    private Comparator<BlsCacheEntry<?>> worthinessComparator;

    private long cacheSizeBytes;

    private CleanupSearchesThread cleanupThread;

    private long lastCacheLogMs = 0;

    private long lastCacheSnapshotMs = 0;

    private String previousCacheStatsMessage = "";

    /** SQLite database to log all our searches to (if enabled) */
    private LogDatabase logDatabase = new LogDatabaseDummy();

    @SuppressWarnings("deprecation")
    public BlsCache(BLSConfigCache config, int maxConcurrentSearches, int abandonedCountAbortTimeSec, boolean trace, LogDatabase logDatabase) {
        this.config = config;
        this.maxConcurrentSearches = maxConcurrentSearches;
        this.abandonedCountAbortTimeSec = abandonedCountAbortTimeSec;
        this.trace = trace;
        this.logDatabase = logDatabase;
        cacheDisabled = config.getMaxJobAgeSec() == 0 || config.getMaxNumberOfJobs() == 0 || config.getMaxSizeMegs() == 0;

        if (!cacheDisabled) {
            worthinessComparator = new Comparator<BlsCacheEntry<?>>() {
                @Override
                public int compare(BlsCacheEntry<?> o1, BlsCacheEntry<?> o2) {
                    long result = o2.worthiness() - o1.worthiness();
                    return result == 0 ? 0 : (result < 0 ? -1 : 1);
                }
            };

            cleanupThread = new CleanupSearchesThread();
            cleanupThread.setDaemon(true); // don't prevent JVM from exiting.
            cleanupThread.start();
        }
    }

    /**
     * Clean up at the end of our lifetime.
     */
    @Override
    public void cleanup() {
        if (cleanupThread != null) {
            cleanupThread.interrupt();
            cleanupThread = null;
        }
        clear(true);
    }

    /**
     * Remove all cache entries for the specified index.
     *
     * @param index the index
     */
    @Override
    public void removeSearchesForIndex(BlackLabIndex index) {
        // Iterate over the entries and remove the ones in the specified index
        traceInfo("Remove searches for index: " + index.name());
        Iterator<Entry<Search<?>, BlsCacheEntry<? extends SearchResult>>> it = searches.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Search<?>, BlsCacheEntry<? extends SearchResult>> entry = it.next();
            if (entry.getValue().search().queryInfo().index() == index) {
                entry.getValue().cancel(true);
                it.remove();
            }
        }
    }

    /**
     * Get rid of all the cached Searches.
     *
     * @param cancelRunning if true, cancels all running searches as well.
     */
    @Override
    public synchronized void clear(boolean cancelRunning) {
        for (BlsCacheEntry<? extends SearchResult> cachedSearch : searches.values()) {
            cachedSearch.cancel(true);
        }
        searches.clear();
        traceInfo("Cache cleared.");
    }

    @Override
    public <R extends SearchResult> BlsCacheEntry<R> getAsync(Search<R> search, boolean allowQueue) {
        return getFromCache(search, false, allowQueue);
    }

    @SuppressWarnings("unchecked")
    private synchronized <R extends SearchResult> BlsCacheEntry<R> getFromCache(Search<R> search, boolean block, boolean allowQueue) {
        //if (trace) logger.debug("getFromCache({}, block={}, allowQueue={})", search, block, allowQueue);
        BlsCacheEntry<R> future;
        boolean useCache = search.queryInfo().useCache() && !cacheDisabled;
        future = useCache ? (BlsCacheEntry<R>) searches.get(search) : null;
        if (future == null) {
            search.log(LogLevel.BASIC, "not found in cache, starting search: " + search);
            int numQueued = numberOfQueuedSearches();
            if (numQueued >= config.getMaxQueuedSearches()) {
                logger.warn("Can't start new search, too many queued searches (numQueued = " + numQueued + ", maxQueuedSearches = " + config.getMaxQueuedSearches() + ")");
                throw new ServerOverloaded("The server is too busy right now (" + numQueued + " searches queued). Please try again later.");
            }

            // Create the cache entry.
            // Note that all entries start "queued" (i.e. the search itself hasn't been started yet).
            // We will see if it can be started below.
            future = new BlsCacheEntry<>(search);
            if (useCache)
                searches.put(search, future);

            // Can we start the search, or should it remain queued for now?
            if (block) {
                // Blocking search. Run it now.
                traceInfo("-- STARTING: {} (BLOCKING SEARCH)", search);
                future.start();
                waitUntilDone(future);
            } else if (!allowQueue || !useCache) {
                // No queueing allowed (i.e. subtask required by another subtask). Start the search right away.
                // (we also do this if you bypass the cache, because then queueing doesn't work)
                if (!allowQueue)
                    traceInfo("-- STARTING: {} (TOP-LEVEL SEARCH)", search);
                else
                    traceInfo("-- STARTING: {} (NOT USING CACHE)", search);
                future.start();
            } else {
                // Queueing is allowed.
                // The new search hasn't been started yet (therefore it is "queued").
                // Check if it (or an older queued search) can be started now.
                startSearchIfPossible(false);
                if (future.wasStarted()) {
                    traceInfo("-- STARTING: {} (QUEUEING NOT NECESSARY)", search);
                } else {
                    traceInfo("-- QUEUEING: {}", search);
                }
            }
        } else {
            // Already in cache.
            traceInfo("-- FOUND:    {}", search);
            future.updateLastAccess();
        }
        //traceCacheStats("   CACHE AFTER GET", false);
        return future;
    }

    synchronized String getCacheStats() {
        if (trace) {
            Map<String, Integer> counts = getCountsPerStatus();
            return String.format("%d queued, %d running, %d finished, %d cancelled",
                counts.get("queued"),
                counts.get("running"),
                counts.get("finished"),
                counts.get("cancelled")
            );
        }
        return "";
    }

    private Map<String, Integer> getCountsPerStatus() {
        int queued = 0, running = 0, finished = 0, cancelled = 0;
        for (BlsCacheEntry<? extends SearchResult> entry: searches.values()) {
            if (!entry.wasStarted())
                queued++;
            else if (entry.isCancelled())
                cancelled++;
            else if (entry.isDone())
                finished++;
            else
                running++;
        }
        Map<String, Integer> countPerStatus = new HashMap<>();
        countPerStatus.put("queued", queued);
        countPerStatus.put("running", running);
        countPerStatus.put("finished", finished);
        countPerStatus.put("cancelled", cancelled);
        return countPerStatus;
    }

    void traceCacheStats(String prompt, boolean onlyIfDifferent) {
        if (trace) {
            String msg = getCacheStats();
            if (!onlyIfDifferent || !msg.equals(previousCacheStatsMessage)) {
                double freeGigs = (double)(MemoryUtil.getFree() * 10 / ONE_GB_BYTES) / 10;
                traceInfo("{}: {}, {}G free heap", prompt, msg, freeGigs);
            }
            previousCacheStatsMessage = msg;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    synchronized public <R extends SearchResult> BlsCacheEntry<R> remove(Search<R> search) {
        BlsCacheEntry<R> future = (BlsCacheEntry<R>) searches.remove(search);
        if (future != null)
            traceInfo("-- REMOVED:  {} ({} searches left)", search, searches.size());
        return future;
    }

    /**
     * Estimate number of result objects (e.g. Hits) in cache.
     *
     * This may not be accurate because Hits are sometimes but not always duplicated
     * between tasks (e.g. gather, sort, group). It gives a rough estimate though
     * that we can use to decide when to clean up.
     *
     * @return estimate of number of Hits in cache
     */
    private synchronized long estimateResultObjectsInCache() {
        // Estimate the total cache size
        long resultsObjectsInCache = 0;
        for (BlsCacheEntry<?> search : searches.values()) {
            resultsObjectsInCache += search.numberOfStoredHits();
        }
        return resultsObjectsInCache;
    }

    void traceInfo(String msg, Object... params) {
        if (trace) {
            logger.info(msg, params);
        }
    }

    public synchronized int numberOfRunningSearches() {
        return (int) searches.values().stream().filter(s -> s.isRunning()).count();
    }

    private synchronized int numberOfQueuedSearches() {
        return (int) searches.values().stream().filter(s -> !s.wasStarted()).count();
    }

    /**
     * If we can start another search, finds the oldest queued search and start it.
     *
     * @param report if true (and trace is on), report the search we started
     */
    synchronized void startSearchIfPossible(boolean report) {
        // Is server load low enough to start a search?
        if (canStartAnotherSearch()) {
            // Find the oldest queued search and start it.
            // Only start one per iteration (give it a little time to start its subtasks)

            // Sort the searches based on descending "worthiness"
            List<BlsCacheEntry<?>> searches = new ArrayList<>(this.searches.values());
            for (BlsCacheEntry<?> s : searches)
                s.calculateWorthiness(); // calculate once before sorting so we don't run into Comparable contract issues because of threading
            searches.sort(worthinessComparator);

            // Find & start oldest queued search
            BlsCacheEntry<?> search1 = searches.stream().filter(s -> !s.wasStarted()).findFirst().orElse(null);
            if (search1 != null) {
                if (report)
                    traceInfo("-- UNQUEUE:  {}", search1);
                search1.start();
            }
        }
    }

    /**
     * See if server load is low enough that we can start (unqueue) another search.
     *
     * @return true if we can start a search, false if it's still too busy
     */
    private boolean canStartAnotherSearch() {
        int runningSearches = numberOfRunningSearches();
        long freeMemory = MemoryUtil.getFree();
        int minFreeMemForSearchMegs = config.getMinFreeMemForSearchMegs();
        boolean enoughMemory = freeMemory / ONE_MB_BYTES >= minFreeMemForSearchMegs;
        boolean threadsAvailable = runningSearches < maxConcurrentSearches;
        boolean canStartSearch = enoughMemory && threadsAvailable;
        return canStartSearch;
    }

    /**
     * Abort searches if too much memory is in use or the search is taking too long.
     * Remove older finished searches from cache. Start a queued search if load is low enough.
     */
    synchronized void updateCache() {
        long resultsObjectsInCache = estimateResultObjectsInCache();
        cacheSizeBytes = resultsObjectsInCache * SIZE_OF_HIT;

        List<BlsCacheEntry<?>> searches = new ArrayList<>(this.searches.values());

        logCacheState();

        // Sort the searches based on descending "worthiness"
        for (BlsCacheEntry<?> s : searches)
            s.calculateWorthiness(); // calculate once before sorting so we don't run into Comparable contract issues because of threading
        searches.sort(worthinessComparator);

        //------------------
        // STEP 1: remove least worthy, finished searches from cache

        // Abort searches that are taking too long (longer than maxSearchTimeSec)
        for (int i = searches.size() - 1; i >= 0; i--) {
            BlsCacheEntry<?> search = searches.get(i);
            int maxSearchTimeSec = config.getMaxSearchTimeSec();
            if (search.isRunning() && search.timeUserWaitedMs() > maxSearchTimeSec * 1000L) {
                // Search is taking too long. Cancel it.
                traceInfo("Search is taking too long (time " + (search.timeUserWaitedMs()/1000) + "s > max time " + maxSearchTimeSec + "s)");
                traceInfo("-- ABORT (taking too long): {}", search);
                String maxTime = BlsUtils.describeIntervalSec(maxSearchTimeSec);
                String retryTime = BlsUtils.describeIntervalSec(config.getDenyAbortedSearchSec());
                search.setReason("Running search aborted because it took longer than " + maxTime + ". " +
                        "This is done to ease server load. If you want to try again, please wait " +
                        retryTime + ".");
                search.cancel(true);
                searches.remove(i);
            }
        }

        // Look at completed searches.
        // Get rid of completed searches that haven't been accessed in a while (maxJobAgeSec).
        // If we're low on memory, remove searches so they will be garbage collected (targetFreeMemMegs).
        boolean checkLastAccessTime = config.getMaxJobAgeSec() >= 0;
        long freeMegs = MemoryUtil.getFree() / ONE_MB_BYTES;
        long memoryToFreeUpMegs = config.getTargetFreeMemMegs() - freeMegs;
        for (int i = searches.size() - 1; i >= 0; i--) {
            BlsCacheEntry<?> search = searches.get(i);
            if (!search.isDone())
                continue;

            boolean isSearchTooOld = false;
            if (search.isCancelled()) {
                // Cancelled (aborted) search kept in cache to prevent clients from resubmitting right away.
                isSearchTooOld = checkLastAccessTime && (search.timeSinceFinishedMs() > config.getDenyAbortedSearchSec() * 1000L ||
                        search.timeSinceCreationMs() > config.getMaxJobAgeSec() * 1000L);
            } else {
                // Finished search.
                isSearchTooOld = checkLastAccessTime && search.timeUnusedMs() > config.getMaxJobAgeSec() * 1000L;
            }
            if (isSearchTooOld || memoryToFreeUpMegs > 0) {
                // Search is too old or cache is too big. Keep removing searches until that's no
                // longer the case
                // logger.debug("Remove from cache: " + search);
                String reason = "?";
                if (memoryToFreeUpMegs > 0) {
                    traceInfo("Not enough free mem (free " + freeMegs + "M < min free "
                            + config.getTargetFreeMemMegs() + "M)");
                    reason = "free up memory";
                } else {
                    traceInfo("Searchjob too old (age " + (int)(search.timeUnusedMs()/1000) + "s > max age "
                            + config.getMaxJobAgeSec() + "s)");
                    reason = "search too old";
                }
                traceInfo("-- REMOVE ({}): {}", reason, search);
                remove(search.search());

                memoryToFreeUpMegs -= (long)search.numberOfStoredHits() * SIZE_OF_HIT / ONE_MB_BYTES; // NB very rough guess, but ok
                searches.remove(i);
            }
        }

        //------------------
        // STEP 2: abort any long-running counts that no client has asked about for a while.
        for (int i = 0; i < searches.size(); i++) {
            BlsCacheEntry<?> search = searches.get(i);
            if (search.isRunning()) {
                // Running search. Run or abort?
                boolean isCount = search.search() instanceof SearchCount;
                if (isCount && search.timeSinceLastAccessMs() > abandonedCountAbortTimeSec * 1000L) {
                    // Abandoned counts are removed right away, because we do this quite quickly (e.g. 30s)
                    // and don't want to penalize users if they decide to come back to this search.
                    remove(search.search());
                    traceInfo("-- ABORT (abandoned count): {}", search);
                    String maxTime = BlsUtils.describeIntervalSec(abandonedCountAbortTimeSec);
                    search.setReason("Running count aborted because no client asked for it for " + maxTime + ". " +
                            "This is done to ease server load. If you need the results of this count, please try your search again.");
                    search.cancel(true);
                    searches.remove(i);
                    i--; // don't skip an element
                }
            }
        }

        // See if we can start a queued search
        startSearchIfPossible(true);

        // Report the cache status (if it changed)
        traceCacheStats("CACHE AFTER UPDATE", true);
    }

    /**
     * Regularly log state of the cache to the log database.
     *
     * Logs the current state every LOG_CACHE_STATE_INTERVAL_SEC, and a snapshot every
     * LOG_CACHE_SNAPSHOT_INTERVAL_SEC.
     *
     * @param cacheSizeBytes
     * @param searches
     */
    private synchronized void logCacheState() {
        // Log cache state
        if (logDatabase != null && System.currentTimeMillis() - lastCacheLogMs > LOG_CACHE_STATE_INTERVAL_SEC * 1000) {
            int numberRunning = 0;
            int largestEntryHits = 0;
            long oldestEntryAgeMs = 0;
            for (BlsCacheEntry<?> s: searches.values()) {
                if (!s.isDone())
                    numberRunning++;
                if (s.numberOfStoredHits() > largestEntryHits)
                    largestEntryHits = s.numberOfStoredHits();
                if (s.timeSinceCreationMs() > oldestEntryAgeMs)
                    oldestEntryAgeMs = s.timeSinceCreationMs();
            }
            lastCacheLogMs = System.currentTimeMillis();
            List<BlsCacheEntry<? extends SearchResult>> snapshot = null;
            if (lastCacheLogMs - lastCacheSnapshotMs > LOG_CACHE_SNAPSHOT_INTERVAL_SEC * 1000) {
                // Every now and then, also capture a cache snapshot
                snapshot = new ArrayList<>(searches.values());
                lastCacheSnapshotMs = lastCacheLogMs;
            }
            logDatabase.addCacheInfo(snapshot, searches.size(), numberRunning, cacheSizeBytes, MemoryUtil.getFree(), (long)largestEntryHits * SIZE_OF_HIT, (int)(oldestEntryAgeMs / 1000));
        }
    }

    /**
     * Dump information about the cache status.
     * @param ds where to write information to
     */
    public synchronized void dataStreamCacheStatus(DataStream ds) {
        Map<String, Integer> counts = getCountsPerStatus();
        ds.startMap();
            ds.entry("targetFreeMemMegs", config.getTargetFreeMemMegs())
            .entry("minFreeMemForSearchMegs", config.getMinFreeMemForSearchMegs())
            .entry("maxQueuedSearches", config.getMaxQueuedSearches())
            .entry("maxSearchTimeSec", config.getMaxSearchTimeSec())
            .entry("maxJobAgeSec", config.getMaxJobAgeSec())
            .entry("maxSearchAgeSec", config.getMaxJobAgeSec())
            .entry("sizeBytes", cacheSizeBytes)
            .entry("numberOfSearches", searches.size())
            .entry("freeMemory", MemoryUtil.getFree())
            .startEntry("countsPerStatus").startMap();
                ds.entry("queued", counts.get("queued"))
                .entry("running", counts.get("running"))
                .entry("finished", counts.get("finished"))
                .entry("cancelled", counts.get("cancelled"));
            ds.endEntry().endMap();
        ds.endMap();
    }

    /**
     * Dump cache contents.
     * @param ds where to write information to
     * @param debugInfo include debug info?
     */
    public synchronized void dataStreamContents(DataStream ds, boolean debugInfo) {
        ds.startList();
        for (BlsCacheEntry<? extends SearchResult> e: searches.values()) {
            ds.startItem("job");
            e.dataStream(ds, debugInfo);
            ds.endItem();
        }
        ds.endList();
    }

    public static void waitUntilDone(BlsCacheEntry<?> entry) {
        try {
            entry.get(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e1) {
            throw new InterruptedSearch(e1);
        } catch (ExecutionException|TimeoutException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        }
    }

}
