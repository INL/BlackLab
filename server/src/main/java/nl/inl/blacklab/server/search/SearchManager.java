package nl.inl.blacklab.server.search;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabEngine;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.logging.LogDatabase;
import nl.inl.blacklab.server.logging.LogDatabaseDummy;
import nl.inl.blacklab.server.logging.LogDatabaseImpl;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages the lifetime of a number of objects needed for the web service.
 */
public class SearchManager {

    private static final Logger logger = LogManager.getLogger(SearchManager.class);

    /** Our config */
    private BLSConfig config;

    /** All running searches as well as recently run searches */
    private SearchCache cache;

    /** System for determining the current user. */
    private AuthManager authSystem;

    /** Manages all the indices we have available and/or open */
    private IndexManager indexMan;

    /** Main BlackLab object, containing the search executor service */
    private BlackLabEngine blackLab;

    /** Database for logging detailed debug information (if enabled) */
    private LogDatabase logDatabase;

    public SearchManager(BLSConfig config) throws ConfigurationException {
        this.config = config;

        // Create BlackLab instance with the desired number of search threads
        int numberOfSearchThreads = config.getPerformance().getMaxConcurrentSearches();
        int maxThreadsPerSearch = config.getPerformance().getMaxThreadsPerSearch();
        blackLab = BlackLab.createEngine(numberOfSearchThreads, maxThreadsPerSearch);

        // Open log database
        try {
            String sqliteDatabase = config.getLog().getSqliteDatabase();
            if (sqliteDatabase != null) {
                File dbFile = new File(sqliteDatabase);
                String url = "jdbc:sqlite:" + dbFile.getCanonicalPath().replaceAll("\\\\", "/");
                Class.forName("org.sqlite.JDBC");
                logDatabase = new LogDatabaseImpl(url);
            } else {
                logDatabase = new LogDatabaseDummy();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error opening log database", e);
        }

        // Create the cache
        String cacheClass = config.getCache().getImplementation();
        cache = createCache(cacheClass, config, blackLab.searchExecutorService(), logDatabase);

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

        try {
            if (logDatabase != null) {
                logDatabase.close();
                logDatabase = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        blackLab.close();
        blackLab = null;

        // Set other variables to null in case it helps GC
        config = null;
        authSystem = null;
        indexMan = null;
    }

    public LogDatabase getLogDatabase() {
        return logDatabase;
    }

    public SearchCache getBlackLabCache() {
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

    private SearchCache createCache(String implementationName, BLSConfig config, ExecutorService executorService, LogDatabase logDb) {
        // If no implementation is set load the BlsCache as the default implementation
        if (StringUtils.isBlank(implementationName)){
            int abandonedCountAbortTimeSec = config.getPerformance().getAbandonedCountAbortTimeSec();
            int maxConcurrentSearches = config.getPerformance().getMaxConcurrentSearches();
            boolean traceCache = config.getLog().getTrace().isCache();
            logger.info("Creating cache with default cache: BlsCache");
            return new BlsCache(config.getCache(), maxConcurrentSearches, abandonedCountAbortTimeSec, traceCache, logDb);
        }

        // Otherwise load a cache implementation as configured in the settings
        String fqClassName = implementationName.contains(".")
            ? implementationName
            : String.format("nl.inl.blacklab.server.search.%s", implementationName);

        try {
            // Load a class via configuration settings
            // by finding a constructor that matches the below arguments
            // if no constructor is found, the creation of the class fails.
            Constructor<?> declaredConstructor = Class.forName(fqClassName)
                .getDeclaredConstructor(BLSConfig.class, ExecutorService.class, LogDatabase.class);
            SearchCache cache = (SearchCache) declaredConstructor
                .newInstance(config, executorService, logDatabase);
            logger.info("Created cache with class: {}", fqClassName);
            return cache;
        } catch (Exception ex) {
            String message = String.format("Can not create cache with class: %s. Did you check your implementation" +
                "matches the expected arguments", fqClassName);
            logger.error(message, ex);
            throw BlackLabRuntimeException.wrap(new ConfigurationException(message));
        }
    }

}
