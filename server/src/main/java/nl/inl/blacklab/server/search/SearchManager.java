package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabEngine;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.index.IndexManager;

/**
 * Manages the lifetime of a number of objects needed for the web service.
 */
public class SearchManager {

    //private static final Logger logger = LogManager.getLogger(SearchManager.class);

    /** Our config */
    private BLSConfig config;

    /** All running searches as well as recently run searches */
    private BlsCache cache;

    /** System for determining the current user. */
    private AuthManager authSystem;

    /** Manages all the indices we have available and/or open */
    private IndexManager indexMan;

    /** Main BlackLab object, containing the search executor service */
    private BlackLabEngine blackLab;

    public SearchManager(BLSConfig config) throws ConfigurationException {
        this.config = config;

        // Create BlackLab instance with the desired number of search threads
        int numberOfSearchThreads = config.getPerformance().getMaxConcurrentSearches();
        int maxThreadsPerSearch = config.getPerformance().getMaxThreadsPerSearch();
        blackLab = BlackLab.createEngine(numberOfSearchThreads, maxThreadsPerSearch);

        // Create the cache
        int abandonedCountAbortTimeSec = config.getPerformance().getAbandonedCountAbortTimeSec();
        int maxConcurrentSearches = config.getPerformance().getMaxConcurrentSearches();
        boolean traceCache = config.getLog().getTrace().isCache();
        cache = new BlsCache(config.getCache(), maxConcurrentSearches, abandonedCountAbortTimeSec, traceCache);

        // Find the indices
        indexMan = new IndexManager(this, config);

        // Init auth system
        authSystem = new AuthManager(config.getAuthentication());
    }

    /**
     * Clean up resources.
     *
     * In particular, stops the load manager thread and cancels any running
     * searches.
     */
    public synchronized void cleanup() {

        // Stop any running searches
        cache.cleanup();
        cache = null;

        blackLab.close();
        blackLab = null;

        // Set other variables to null in case it helps GC
        config = null;
        authSystem = null;
        indexMan = null;
    }

    public BlsCache getBlackLabCache() {
        return cache;
    }

    public BLSConfig config() {
        return config;
    }

    public AuthManager getAuthSystem() {
        return authSystem;
    }

    public IndexManager getIndexManager() {
        return indexMan;
    }

    public BlackLabEngine blackLabInstance() {
        return blackLab;
    }

}
