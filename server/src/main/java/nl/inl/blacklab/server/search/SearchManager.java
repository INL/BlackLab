package nl.inl.blacklab.server.search;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.Job;
import nl.inl.blacklab.server.jobs.JobDescription;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;

public class SearchManager {

    private static final Logger logger = LogManager.getLogger(SearchManager.class);

    /** Our config */
    private BlsConfig config;

    /** All running searches as well as recently run searches */
    private BlsSearchCache cache;

    /** System for determining the current user. */
    private AuthManager authSystem;

    /** Manages all the indices we have available and/or open */
    private IndexManager indexMan;

    public SearchManager(JsonNode properties) throws ConfigurationException {
        logger.debug("SearchManager created");

        // The main config object
        config = new BlsConfig(properties);

        // Create the cache
        // Use the performance properties [optional, defaults will be used if missing]
        cache = new BlsSearchCache(this, config.getCacheConfig());

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
        cache.cleanup();

        // Set variables to null in case it helps GC
        cache = null;
        config = null;
        authSystem = null;
        indexMan = null;
    }

    public BlsSearchCache getCache() {
        return cache;
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

    public Job search(User user, JobDescription jobDesc) throws BlsException {
        return cache.searchBlocking(user, jobDesc);
    }
    
    public Job searchNonBlocking(User user, JobDescription jobDesc) throws BlsException {
        return cache.searchNonBlocking(user, jobDesc);
    }

}
