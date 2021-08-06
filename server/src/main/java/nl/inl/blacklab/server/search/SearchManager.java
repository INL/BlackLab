package nl.inl.blacklab.server.search;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabEngine;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.config.BLSConfigParameters;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.logging.LogDatabase;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;

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
        cache = new BlsCache(config);

        // Find the indices
        indexMan = new IndexManager(this, config);

        // Init auth system
        authSystem = new AuthManager(config.getAuthentication());

        // Set up the parameter default values
        BLSConfigParameters param = config.getParameters();
        SearchParameters.setDefault("number", "" + param.getPageSize().getDefaultValue());
        SearchParameters.setDefault("wordsaroundhit", "" + param.getContextSize().getDefaultValue());
        SearchParameters.setDefault("maxretrieve", "" + param.getProcessHits().getDefaultValue());
        SearchParameters.setDefault("maxcount", "" + param.getCountHits().getDefaultValue());
        SearchParameters.setDefault("sensitive", param.getDefaultSearchSensitivity() == MatchSensitivity.SENSITIVE ? "yes" : "no");
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

    public <T extends SearchResult> T search(User user, Search<T> search) throws BlsException {
        try {
            return search.execute();
        } catch (InvalidQuery e) {
            throw new BadRequest("INVALID_QUERY", "Invalid query: " + e.getMessage());
        }
    }

    public <T extends SearchResult> BlsCacheEntry<T> searchNonBlocking(User user, Search<T> search) {
        return (BlsCacheEntry<T>)search.executeAsync();
    }

    public void setLogDatabase(LogDatabase logDatabase) {
        cache.setLogDatabase(logDatabase);
    }

    public BlackLabEngine blackLabInstance() {
        return blackLab;
    }

}
