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
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InsufficientMemoryAvailable;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.config.BLSConfigCache;
import nl.inl.blacklab.server.config.BLSConfigPerformance;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.logging.LogDatabase;
import nl.inl.blacklab.server.util.MemoryUtil;
import nl.inl.util.ThreadPauser;
import nl.inl.util.ThreadPauserImpl;

public class BlsCache implements SearchCache {
    
    private static final int ONE_MINUTE_MS = 60000;

    private static final Logger logger = LogManager.getLogger(BlsCache.class);
    
    public static final boolean ENABLE_NEW_CACHE = true;

    /** Very rough measure of how large result objects are, based on a Hit (4 ints + 12 bytes object overhead) */
    public static final int SIZE_OF_HIT = 28;

    protected Map<Search<?>, BlsCacheEntry<? extends SearchResult>> searches = new HashMap<>();
    
    protected boolean trace = false;

    private boolean cacheDisabled;
    
    private LogDatabase logDatabase = null;

    public BlsCache(BLSConfig config) {
        BLSConfigCache cacheConfig = config.getCache();
        initLoadManagement(cacheConfig, config.getPerformance());
        this.trace = config.getLog().getTrace().isCache();
        cacheDisabled = cacheConfig.getMaxNumberOfJobs() == 0 || cacheConfig.getMaxJobAgeSec() == 0 || cacheConfig.getMaxSizeMegs() == 0;
    }

    public void setLogDatabase(LogDatabase logDatabase) {
        this.logDatabase = logDatabase;
    }

    /**
     * Clean up at the end of our lifetime.
     */
    @Override
    public void cleanup() {
        loadManagerThread.interrupt();
        loadManagerThread = null;
        clear(true);
    }
    
    /**
     * Remove all cache entries for the specified index.
     *
     * @param index the index
     */
    @Override
    public synchronized void removeSearchesForIndex(BlackLabIndex index) {
        // Iterate over the entries and remove the ones in the specified index
        Iterator<Entry<Search<?>, BlsCacheEntry<? extends SearchResult>>> it = searches.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Search<?>, BlsCacheEntry<? extends SearchResult>> entry = it.next();
            if (entry.getValue().search().queryInfo().index() == index) {
                if (!entry.getValue().isSearchDone())
                    entry.getValue().cancelSearch();
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
            if (!cachedSearch.isSearchDone())
                cachedSearch.cancelSearch();
        }
        searches.clear();
        logger.debug("Cache cleared.");
    }    
    
    public boolean isTrace() {
        return trace;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    @Override
    public <R extends SearchResult> BlsCacheEntry<R> getAsync(Search<R> search, Supplier<R> searchTask) {
        return getFromCache(search, searchTask, false);
    }

    @Override
    public <R extends SearchResult> R get(Search<R> search, Supplier<R> searchTask) throws ExecutionException {
        BlsCacheEntry<R> entry = getFromCache(search, searchTask, true);
        try {
            return entry.get();
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <R extends SearchResult> BlsCacheEntry<R> getFromCache(Search<R> search,
            Supplier<R> searchTask, boolean block) {
        BlsCacheEntry<R> future;
        boolean created = false;
        boolean useCache = search.queryInfo().useCache();
        synchronized (this) {
            future = useCache ? (BlsCacheEntry<R>) searches.get(search) : null;
            if (future == null) {
                search.log(LogLevel.BASIC, "not found in cache, starting search: " + search);
                try {
                    checkFreeMemory(); // check that we have sufficient available memory
                } catch (InsufficientMemoryAvailable e) {
                    search.log(LogLevel.BASIC, "not enough memory for search: " + search + " (" + e.getMessage() + ")");
                    throw e;
                }
                future = new BlsCacheEntry<>(search, searchTask);
                created = true;
                if (!cacheDisabled && useCache)
                    searches.put(search, future);
                if (!block)
                    future.start(false);
            } else {
                search.log(LogLevel.BASIC, "found in cache (" + future.status() + "): " + search);
            }
        }
        if (created) {
            if (trace)
                logger.info("-- ADDED: " + search);
            if (block)
                future.start(true);
        } else {
            if (trace)
                logger.info("-- FOUND: " + search);
            future.updateLastAccess();
        }
        return future;
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <R extends SearchResult> BlsCacheEntry<R> remove(Search<R> search) {
        BlsCacheEntry<R> future = null;
        synchronized (this) {
            future = (BlsCacheEntry<R>) searches.remove(search);
            if (future != null && trace)
                logger.info("-- REMOVED: " + search);
        }
        return future;
    }

    // CACHE / LOAD MANAGEMENT
    //----------------------------------------------------

    /**
     * A thread that regularly calls performLoadManagement() to
     * ensure that load management continues even if no new requests are coming in.
     */
    class LoadManagerThread extends Thread implements UncaughtExceptionHandler {

        /** Construct the load manager thread object. */
        public LoadManagerThread() {
            setUncaughtExceptionHandler(this);
        }

        /**
         * Run the thread, performing the requested search.
         */
        @Override
        public void run() {
            while (!interrupted()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    logger.info("LOADMGR interrupted");
                    return;
                }

                performLoadManagement();
            }
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            logger.error("LoadManagerThread threw an exception!");
            e.printStackTrace();
        }

    }

    /**
     * What we can do to a query in response to the server load.
     */
    public static enum ServerLoadQueryAction {
        UNPAUSE, // unpause search (run normally)
        PAUSE,   // pause search
        ABORT,   // abort search
    }

    private BLSConfigCache config;
    
    private BLSConfigPerformance perfConfig;
    
    private Comparator<BlsCacheEntry<?>> wortinessComparator;

    private int resultsObjectsInCache;

    private LoadManagerThread loadManagerThread;

    private long lastCacheLog = 0;

    private long lastCacheSnapshot = 0;
    
    private void initLoadManagement(BLSConfigCache config, BLSConfigPerformance perfConfig) {
        this.config = config;
        this.perfConfig = perfConfig;
        
        // Make sure long operations can be paused.
        ThreadPauserImpl.setEnabled(perfConfig.isPausingEnabled());
        finishInit();
    }

    private void finishInit() {
        wortinessComparator = new Comparator<BlsCacheEntry<?>>() {
            @Override
            public int compare(BlsCacheEntry<?> o1, BlsCacheEntry<?> o2) {
                long result = o2.worthiness() - o1.worthiness();
                return result == 0 ? 0 : (result < 0 ? -1 : 1);
            }
        };
        
        loadManagerThread = new LoadManagerThread();
        loadManagerThread.start();
    }

    private synchronized int determineCacheSize() {
        // Estimate the total cache size
        resultsObjectsInCache = 0;
        for (BlsCacheEntry<?> search : searches.values()) {
            resultsObjectsInCache += search.numberOfStoredHits();
        }
        return resultsObjectsInCache;
    }
    
    /**
     * Evaluate what we need to do (if anything) with each search given the current
     * server load.
     */
    synchronized void performLoadManagement() {
        
        determineCacheSize();
        long cacheSizeBytes = (long)resultsObjectsInCache * SIZE_OF_HIT;

        List<BlsCacheEntry<?>> searches = new ArrayList<>(this.searches.values());
        int numberOfSearchesInCache = searches.size();

        // Log cache state every 60s
        if (logDatabase != null && System.currentTimeMillis() - lastCacheLog > ONE_MINUTE_MS) {
            int numberRunning = 0, numberPaused = 0;
            int largestEntryHits = 0;
            long oldestEntryAgeMs = 0;
            for (BlsCacheEntry<?> s: searches) {
                if (!s.isSearchDone())
                    numberRunning++;
                if (s.threadPauser().isPaused())
                    numberPaused++;
                if (s.numberOfStoredHits() > largestEntryHits)
                    largestEntryHits = s.numberOfStoredHits();
                if (s.timeSinceCreation() > oldestEntryAgeMs)
                    oldestEntryAgeMs = s.timeSinceCreation();
            }
            lastCacheLog = System.currentTimeMillis();
            List<BlsCacheEntry<? extends SearchResult>> snapshot = null;
            if (lastCacheLog - lastCacheSnapshot > ONE_MINUTE_MS * 5) {
                // Capture a cache snapshot every 5 minutes
                snapshot = searches;
                lastCacheSnapshot = lastCacheLog;
            }
            logDatabase.addCacheInfo(snapshot, searches.size(), numberRunning, numberPaused, cacheSizeBytes, MemoryUtil.getFree(), (long)largestEntryHits * SIZE_OF_HIT, (int)(oldestEntryAgeMs / 1000));
        }

        // Sort the searches based on descending "worthiness"
        for (BlsCacheEntry<?> s : searches)
            s.calculateWorthiness(); // calculate once before sorting so we don't run into Comparable contract issues because of threading
        searches.sort(wortinessComparator);

        //------------------
        // STEP 1: remove least worthy, finished searches from cache

        // If we're low on memory, remove searches from cache until we're not.
        long freeMegs = MemoryUtil.getFree() / 1000000;
        long memoryToFreeUp = config.getTargetFreeMemMegs() - freeMegs;

        // Look at searches from least worthy to worthiest.
        // Get rid of old searches
        List<BlsCacheEntry<?>> removed = new ArrayList<>();
        boolean lookAtCacheSizeAndSearchAccessTime = true;
        for (int i = searches.size() - 1; i >= 0; i--) {
            BlsCacheEntry<?> search1 = searches.get(i);

            if (!search1.isSearchDone() && search1.timeUserWaited() > config.getMaxSearchTimeSec() * 1000L) {
                // Search is taking too long. Cancel it.
                if (trace) {
                    logger.debug("Search is taking too long (time " + (search1.timeUserWaited()/1000) + "s > max time "
                            + config.getMaxSearchTimeSec() + "s)");
                    logger.debug("  Cancelling searchjob: " + search1);
                }
                remove(search1.search());
                cacheSizeBytes -= search1.numberOfStoredHits() * SIZE_OF_HIT;
                numberOfSearchesInCache--;
                removed.add(search1);
                search1.cancelSearch();
            } else if (search1.isDone()) {
                // Finished search
                boolean removeBecauseOfCacheSizeOrAge = false;
                boolean isCacheTooBig = false;
                boolean isSearchTooOld = false;
                long cacheSizeMegs = 0;
                boolean tooMuchMemory = false;
                boolean tooManySearches = false;
                if (lookAtCacheSizeAndSearchAccessTime) {
                    tooManySearches = config.getMaxNumberOfJobs() >= 0
                            && numberOfSearchesInCache > config.getMaxNumberOfJobs();
                    cacheSizeMegs = cacheSizeBytes / 1000000;
                    tooMuchMemory = config.getMaxSizeMegs() >= 0
                            && cacheSizeMegs > config.getMaxSizeMegs();
                    isCacheTooBig = tooManySearches || tooMuchMemory;
                    isSearchTooOld = false;
                    if (!isCacheTooBig) {
                        boolean tooOld = config.getMaxJobAgeSec() * 1000L >= 0
                                && search1.timeUnused() > config.getMaxJobAgeSec() * 1000L;
                        isSearchTooOld = tooOld;
                    }
                    removeBecauseOfCacheSizeOrAge = isCacheTooBig || isSearchTooOld;
                }
                if (memoryToFreeUp > 0 || removeBecauseOfCacheSizeOrAge) {
                    // Search is too old or cache is too big. Keep removing searches until that's no
                    // longer the case
                    // logger.debug("Remove from cache: " + search);
                    if (trace) {
                        if (memoryToFreeUp > 0)
                            logger.debug("Not enough free mem (free " + freeMegs + "M < min free "
                                    + config.getTargetFreeMemMegs() + "M)");
                        else if (tooManySearches)
                            logger.debug("Too many searches in cache (" + searches.size() + " > max size "
                                    + config.getMaxNumberOfJobs() + ")");
                        else if (tooMuchMemory)
                            logger.debug("Cache too large (size " + cacheSizeMegs + "M > max size "
                                    + config.getMaxSizeMegs() + "M)");
                        else
                            logger.debug("Searchjob too old (age " + (int)(search1.timeUnused()/1000) + "s > max age "
                                    + config.getMaxJobAgeSec() + "s)");
                        logger.debug("  Removing searchjob: " + search1);
                    }
                    remove(search1.search());
                    cacheSizeBytes -= search1.numberOfStoredHits() * SIZE_OF_HIT;
                    numberOfSearchesInCache--;
                    removed.add(search1);
                    memoryToFreeUp -= search1.numberOfStoredHits() * SIZE_OF_HIT;
                    
                } else {
                    // Cache is no longer too big and these searches are not too old. Stop checking
                    // that,
                    // just check for long-running searches
                    lookAtCacheSizeAndSearchAccessTime = false;
                }
            }
        }
        // Make sure we don't look at the searches we removed again in the next step
        for (BlsCacheEntry<?> r : removed) {
            searches.remove(r);
        }
        // NOTE: we used to hint the Java GC to run, but this caused severe
        // slowdowns. It's better to rely on the incremental garbage collection.

        //------------------
        // STEP 2: make sure the most worthy searches get the CPU, and pause
        //         any others to avoid bringing down the server.

        // LEXION CHANGE: we've commented out step 2, as it causes deadlocks under heavy load.

//        int coresLeft = perfConfig.getMaxConcurrentSearches();
//        int pauseSlotsLeft = perfConfig.getMaxPausedSearches();
//        for (BlsCacheEntry<?> search : searches) {
//            if (search.isDone()) {
//                // Finished search. Keep in cache?
//
//                // NOTE: we'll leave this to removeOldSearches() for now.
//                // Later we'll integrate the two.
//            } else {
//                // Running search. Run, pause or abort?
//                boolean isCount = search.search() instanceof SearchCount;
//                if (isCount && search.timeSinceLastAccess() > perfConfig.getAbandonedCountPauseTimeSec() * 1000L
//                        && pauseSlotsLeft > 0) {
//                    // This is a long-running count that seems to have been abandoned by the client.
//                    // First we'll pause it so it doesn't consume CPU resources. Eventually we'll
//                    // abort it so its memory is freed up.
//                    if (search.timeSinceLastAccess() <= perfConfig.getAbandonedCountAbortTimeSec() * 1000L) {
//                        pauseSlotsLeft--;
//                        applyAction(search, ServerLoadQueryAction.PAUSE, "abandoned count");
//                    } else {
//                        applyAction(search, ServerLoadQueryAction.ABORT, "abandoned count");
//                    }
//                } else if (coresLeft > 0) {
//                    // A core is available. Run the search.
//                    coresLeft--;
//                    applyAction(search, ServerLoadQueryAction.UNPAUSE, "core available");
//                } else if (pauseSlotsLeft > 0) {
//                    // No cores, but a pause slot is left. Pause it.
//                    pauseSlotsLeft--;
//                    applyAction(search, ServerLoadQueryAction.PAUSE, "no cores left");
//                } else {
//                    // No cores or pause slots. Abort the search.
//                    applyAction(search, ServerLoadQueryAction.ABORT, "no cores or pause slots left");
//                }
//            }
//        }
    }

    /**
     * Apply one of the load managing actions to a search.
     *
     * @param search the search
     * @param action the action to apply
     * @param reason the reason for this action, so we can log it
     */
    private void applyAction(BlsCacheEntry<?> search, ServerLoadQueryAction action, String reason) {
        // See what to do with the current search
        ThreadPauser threadPauser = search.threadPauser();
        switch (action) {
        case UNPAUSE:
            if (threadPauser.isPaused()) {
                if (trace)
                    logger.debug("LOADMGR: Resuming search: " + search + " (" + reason + ")");
                threadPauser.pause(false);
            }
            break;
        case PAUSE:
            if (!threadPauser.isPaused()) {
                if (trace)
                    logger.debug("LOADMGR: Pausing search: " + search + " (" + reason + ")");
                threadPauser.pause(true);
            }
            break;
        case ABORT:
            if (!search.isSearchDone()) {
                // TODO: Maybe we should blacklist certain searches for a time?
                if (trace)
                    logger.warn("LOADMGR: Aborting search: " + search + " (" + reason + ")");
                remove(search.search());
                search.cancelSearch();
            }
            break;
        }
    }
    
    private void checkFreeMemory() {
        long freeMegs = MemoryUtil.getFree() / 1000000;
        if (freeMegs < config.getMinFreeMemForSearchMegs()) {
            performLoadManagement(); //removeOldSearches(); // try to free up space for next search
            logger.warn(
                    "Can't start new search, not enough memory (" + freeMegs + "M < "
                            + config.getMinFreeMemForSearchMegs() + "M)");
            logger.warn("(NOTE: make sure Tomcat's max heap mem is set to an appropriate value!)");
            throw new InsufficientMemoryAvailable(
                    "The server has insufficient memory available to start a new search. Please try again later. (not enough JVM heap memory for new search; try increasing -Xmx value when starting JVM)");
        }
        // logger.debug("Enough free memory: " + freeMegs + "M");
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
                .entry("sizeBytes", resultsObjectsInCache * SIZE_OF_HIT)
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
