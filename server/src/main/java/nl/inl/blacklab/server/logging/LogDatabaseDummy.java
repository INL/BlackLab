package nl.inl.blacklab.server.logging;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.requestlogging.SearchLogger;

public class LogDatabaseDummy implements LogDatabase {

    /**
     * Get requests in an interval, ordered by time.
     * 
     * @param from start of interval
     * @param to end of interval
     * @return requests
     */
    @Override
    public List<Request> getRequests(long from, long to) {
        return Collections.emptyList();
    }

    /**
     * Get requests in an interval, ordered by time.
     * 
     * @param from start of interval
     * @param to end of interval
     * @return requests
     */
    @Override
    public List<CacheStats> getCacheStats(long from, long to) {
        return Collections.emptyList();
    }

    /**
     * Close connection.
     */
    @Override
    public void close() throws IOException {
        // NOP
    }

    /**
     * Add a request and return a handle for logging.
     * 
     * @param corpus corpus we're searching
     * @param type search type, e.g. hits
     * @param parameters search parameters
     * @return handle for logging
     */
    @Override
    public SearchLogger addRequest(String corpus, String type, Map<String, String[]> parameters) {
        return new SearchLogger() {
            @Override
            public void log(String line) {
                // NOP
            }

            @Override
            public void setResultsFound(int resultsFound) {
                // NOP
            }

            @Override
            public void close() throws IOException {
                // NOP
            }
        };
    }

    /**
     * Log info about the state of the cache.
     * 
     * @param numberOfSearches number of cached searches
     * @param numberRunning number of running searches
     * @param numberPaused number of paused searches
     * @param sizeBytes cache size in bytes
     * @param freeMemoryBytes free Java heap memory in bytes
     * @param largestEntryBytes largest cache entry
     * @param oldestEntryAgeSec oldest cache entry
     */
    @Override
    public void addCacheInfo(int numberOfSearches, int numberRunning, int numberPaused, long sizeBytes, long freeMemoryBytes,
            long largestEntryBytes, int oldestEntryAgeSec) {
        // NOP
    }

}
