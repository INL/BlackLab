package nl.inl.blacklab.server.search;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabEngine;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.logging.LogDatabase;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;

public class SearchManager {

    private static final Logger logger = LogManager.getLogger(SearchManager.class);

    /** Our config */
    private BlsConfig config;

//    /** All running searches as well as recently run searches */
//    private BlsSearchCache cache;

    /** All running searches as well as recently run searches */
    private BlsCache newCache;
    
    /** System for determining the current user. */
    private AuthManager authSystem;

    /** Manages all the indices we have available and/or open */
    private IndexManager indexMan;

    /** Main BlackLab object, containing the search executor service */
    private BlackLabEngine blackLab;
    
    public SearchManager(JsonNode properties) throws ConfigurationException {
        logger.debug("SearchManager created");

        // The main config object
        config = new BlsConfig(properties);
        
        // Create BlackLab instance with the desired number of search threads
        int numberOfSearchThreads = config.getCacheConfig().getMaxConcurrentSearches();
        blackLab = BlackLab.create(numberOfSearchThreads);

        // Create the cache
        // Use the performance properties [optional, defaults will be used if missing]
//        cache = new BlsSearchCache(this, config.getCacheConfig());
        newCache = new BlsCache(config.getCacheConfig());

        // Find the indices
        indexMan = new IndexManager(this, properties);

        // Init auth system
        authSystem = new AuthManager(config.getAuthClass(), config.getAuthParam());
        
        // Set up the parameter default values
        SearchParameters.setDefault("number", "" + config.defaultPageSize());
        SearchParameters.setDefault("wordsaroundhit", "" + config.getDefaultContextSize());
        SearchParameters.setDefault("maxretrieve", "" + config.getDefaultMaxHitsToRetrieve());
        SearchParameters.setDefault("maxcount", "" + config.getDefaultMaxHitsToCount());
        SearchParameters.setDefault("sensitive", config.defaultMatchSensitivity() == MatchSensitivity.SENSITIVE ? "yes" : "no");
    }

    /**
     * Clean up resources.
     *
     * In particular, stops the load manager thread and cancels any running
     * searches.
     */
    public synchronized void cleanup() {
        // Stop any running searches
        newCache.cleanup();
        newCache = null;
        
//        cache.cleanup();
//        cache = null;

        // Set other variables to null in case it helps GC
        config = null;
        authSystem = null;
        indexMan = null;
    }

//    public BlsSearchCache getCache() {
//        return cache;
//    }

    public BlsCache getBlackLabCache() {
        return newCache;
    }

    public BlsConfig config() {
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
        newCache.setLogDatabase(logDatabase);
    }

    public BlackLabEngine blackLabInstance() {
        return blackLab;
    }

}
