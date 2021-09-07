package nl.inl.blacklab.server.search;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InsufficientMemoryAvailable;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.config.BLSConfigCache;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.logging.LogDatabase;
import nl.inl.blacklab.server.util.MemoryUtil;

public class BlsCache implements SearchCache {

    private static final Logger logger = LogManager.getLogger(BlsCache.class);

    /** Milliseconds per minute */
    private static final int LOG_CACHE_STATE_INTERVAL_SEC = 60;

    private static final int LOG_CACHE_SNAPSHOT_INTERVAL_SEC = 60 * 5;

    /** Bytes in one megabyte */
    private static final long ONE_MB_BYTES = 1_000_000;

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
            super("CleanupSearchesThread");
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

                cleanupSearches();
            }
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.error("CleanupSearchesThread threw an exception!");
            e.printStackTrace();
        }

    }

    protected Map<Search<?>, BlsCacheEntry<? extends SearchResult>> searches = new HashMap<>();

    protected boolean trace = false;

    private boolean cacheDisabled;

    private LogDatabase logDatabase = null;

    public BlsCache(BLSConfigCache config, int abandonedCountAbortTimeSec, boolean trace) {
        this.config = config;
        this.abandonedCountAbortTimeSec = abandonedCountAbortTimeSec;
        this.trace = trace;
        cacheDisabled = config.getMaxNumberOfJobs() == 0 || config.getMaxJobAgeSec() == 0 || config.getMaxSizeMegs() == 0;

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

    public void setLogDatabase(LogDatabase logDatabase) {
        this.logDatabase = logDatabase;
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
    public void clear(boolean cancelRunning) {
        for (BlsCacheEntry<? extends SearchResult> cachedSearch : searches.values()) {
            cachedSearch.cancel(true);
        }
        searches.clear();
        logger.debug("Cache cleared.");
    }

    @Override
    public <R extends SearchResult> BlsCacheEntry<R> getAsync(Search<R> search) {
        return getFromCache(search, false);
    }

    @SuppressWarnings("unchecked")
    private <R extends SearchResult> BlsCacheEntry<R> getFromCache(Search<R> search, boolean block) {
        BlsCacheEntry<R> future;
        boolean useCache = search.queryInfo().useCache() && !cacheDisabled;
        synchronized (this) {
            future = useCache ? (BlsCacheEntry<R>) searches.get(search) : null;
            if (future == null) {
                search.log(LogLevel.BASIC, "not found in cache, starting search: " + search);
                try {
                    long freeMegs = MemoryUtil.getFree() / ONE_MB_BYTES;
                    if (freeMegs < config.getMinFreeMemForSearchMegs()) {
                        cleanupSearches();  // try to free up space for next search
                        logger.warn(
                                "Can't start new search, not enough memory (" + freeMegs + "M < "
                                        + config.getMinFreeMemForSearchMegs() + "M)");
                        logger.warn("(NOTE: make sure Tomcat's max heap mem is set to an appropriate value!)");
                        throw new InsufficientMemoryAvailable(
                                "The server has insufficient memory available to start a new search. Please try again later. (not enough JVM heap memory for new search; try increasing -Xmx value when starting JVM)");
                    }
                    // logger.debug("Enough free memory: " + freeMegs + "M"); // check that we have sufficient available memory
                } catch (InsufficientMemoryAvailable e) {
                    search.log(LogLevel.BASIC, "not enough memory for search: " + search + " (" + e.getMessage() + ")");
                    throw e;
                }
                future = new BlsCacheEntry<>(search);
                if (useCache)
                    searches.put(search, future);
                if (trace) logger.info("-- STARTING: {}", search);
                future.start(block);
            } else {
                if (trace) logger.info("-- FOUND: {}", search);
                future.updateLastAccess();
            }
        }
        return future;
    }

    @Override
    @SuppressWarnings("unchecked")
    synchronized public <R extends SearchResult> BlsCacheEntry<R> remove(Search<R> search) {
        BlsCacheEntry<R> future = (BlsCacheEntry<R>) searches.remove(search);
        if (future != null && trace)
            logger.info("-- REMOVED: " + search + " (" + searches.size() + " searches left)");
        return future;
    }

    private BLSConfigCache config;

    private Comparator<BlsCacheEntry<?>> worthinessComparator;

    private long cacheSizeBytes;

    private CleanupSearchesThread cleanupThread;

    private long lastCacheLogMs = 0;

    private long lastCacheSnapshotMs = 0;

    /** Abort an abandoned count after how much time? (s) */
    private int abandonedCountAbortTimeSec;

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

    void dbgtrace(String msg) {
        if (trace) {
            logger.debug(msg);
        }
    }

    /**
     * Abort searches if too much memory is in use or the search is taking too long.
     * Remove older finished searches from cache.
     */
    synchronized void cleanupSearches() {

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

        // If we're low on memory, remove searches from cache until we're not.
        long freeMegs = MemoryUtil.getFree() / ONE_MB_BYTES;
        long memoryToFreeUpMegs = config.getTargetFreeMemMegs() - freeMegs;

        // Look at searches from least worthy to worthiest.
        // Get rid of old searches
        boolean lookAtCacheSizeAndSearchAccessTime = true;
        for (int i = searches.size() - 1; i >= 0; i--) {
            BlsCacheEntry<?> search1 = searches.get(i);

            boolean runningSearchTakingTooLong = !search1.isDone() && search1.timeUserWaitedMs() > config.getMaxSearchTimeSec() * 1000L;
            boolean initialSearchDone = search1.isDone();
            if (runningSearchTakingTooLong) {
                // Search is taking too long. Cancel it.
                dbgtrace("Search is taking too long (time " + (search1.timeUserWaitedMs()/1000) + "s > max time " + config.getMaxSearchTimeSec() + "s)");
                abortSearch(search1, "taking too long");
                searches.remove(i);

            } else if (initialSearchDone) {
                // Finished its initial search, but may still be gathering hits.
                boolean removeBecauseOfCacheSizeOrAge = false;
                boolean isCacheTooBig = false;
                boolean isSearchTooOld = false;
                long cacheSizeMegs = 0;
                boolean tooMuchMemory = false;
                boolean tooManySearches = false;
                if (lookAtCacheSizeAndSearchAccessTime) {
                    tooManySearches = config.getMaxNumberOfJobs() >= 0 && searches.size() > config.getMaxNumberOfJobs();
                    cacheSizeMegs = cacheSizeBytes / ONE_MB_BYTES;
                    tooMuchMemory = config.getMaxSizeMegs() >= 0
                            && cacheSizeMegs > config.getMaxSizeMegs();
                    isCacheTooBig = tooManySearches || tooMuchMemory;
                    isSearchTooOld = false;
                    if (!isCacheTooBig) {
                        boolean tooOld = config.getMaxJobAgeSec() * 1000L >= 0
                                && search1.timeUnusedMs() > config.getMaxJobAgeSec() * 1000L;
                        isSearchTooOld = tooOld;
                    }
                    removeBecauseOfCacheSizeOrAge = isCacheTooBig || isSearchTooOld;
                }
                if (memoryToFreeUpMegs > 0 || removeBecauseOfCacheSizeOrAge) {
                    // Search is too old or cache is too big. Keep removing searches until that's no
                    // longer the case
                    // logger.debug("Remove from cache: " + search);
                    String reason = "?";
                    if (memoryToFreeUpMegs > 0) {
                        dbgtrace("Not enough free mem (free " + freeMegs + "M < min free "
                                + config.getTargetFreeMemMegs() + "M)");
                        reason = "free up memory";
                    } else if (tooManySearches) {
                        dbgtrace("Too many searches in cache (" + searches.size() + " > max size "
                                + config.getMaxNumberOfJobs() + ")");
                        reason = "too many searches";
                    } else if (tooMuchMemory) {
                        dbgtrace("Cache too large (size " + cacheSizeMegs + "M > max size "
                                + config.getMaxSizeMegs() + "M)");
                        reason = "cache too large";
                    } else {
                        dbgtrace("Searchjob too old (age " + (int)(search1.timeUnusedMs()/1000) + "s > max age "
                                + config.getMaxJobAgeSec() + "s)");
                        reason = "search too old";
                    }
                    abortSearch(search1, reason);
                    memoryToFreeUpMegs -= (long)search1.numberOfStoredHits() * SIZE_OF_HIT / ONE_MB_BYTES;
                    searches.remove(i);

                } else {
                    // Cache is no longer too big and these searches are not too old. Stop checking
                    // that and just check for long-running searches.
                    lookAtCacheSizeAndSearchAccessTime = false;
                }
            } else {
                // Still doing initial search, which we can't interrupt anyway.
            }
        }

        //------------------
        // STEP 2: abort any long-running counts that no client has asked about for a while.
        for (int i = 0; i < searches.size(); i++) {
            BlsCacheEntry<?> search = searches.get(i);
            if (!search.isDone()) {
                // Running search. Run or abort?
                boolean isCount = search.search() instanceof SearchCount;
                if (isCount && search.timeSinceLastAccessMs() > abandonedCountAbortTimeSec * 1000L) {
                    abortSearch(search, "abandoned count");
                    searches.remove(i);
                    i--; // don't skip an element
                }
            }
        }
    }

    /**
     * Abort a search.
     *
     * @param search the search
     * @param reason the reason for aborting it, so we can log it
     */
    private void abortSearch(BlsCacheEntry<?> search, String reason) {
        // TODO: Maybe we should blacklist certain searches for a time?
        dbgtrace("CleanupSearchesThread: aborting search: " + search + " (" + reason + ")");
        remove(search.search());
        search.cancel(true);
        cacheSizeBytes -= (long)search.numberOfStoredHits() * SIZE_OF_HIT;
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
    private void logCacheState() {
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
        long maxSizeMegs = config.getMaxSizeMegs();
        long maxSizeBytes = maxSizeMegs < 0 ? -1 : maxSizeMegs * 1000 * 1000;
        ds.startMap()
                .entry("maxSizeBytes", maxSizeBytes)
                .entry("maxNumberOfSearches", config.getMaxNumberOfJobs())
                .entry("maxSearchAgeSec", config.getMaxJobAgeSec())
                .entry("sizeBytes", cacheSizeBytes)
                .entry("numberOfSearches", searches.size())
                .entry("freeMemory", MemoryUtil.getFree())
                .endMap();
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

}
