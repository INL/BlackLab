package nl.inl.blacklab.server.logging;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.requestlogging.SearchLogger;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.server.search.BlsCacheEntry;

public interface LogDatabase {

    /**
     * Get requests in an interval, ordered by time.
     *
     * @param from start of interval
     * @param to end of interval
     * @return requests
     */
    List<Request> getRequests(long from, long to);

    /**
     * Get requests in an interval, ordered by time.
     *
     * @param from start of interval
     * @param to end of interval
     * @return requests
     */
    List<CacheStats> getCacheStats(long from, long to);

    /**
     * Close connection.
     * @throws IOException on error
     */
    void close() throws IOException;

    /**
     * Add a request and return a handle for logging.
     *
     * @param corpus corpus we're searching
     * @param type search type, e.g. hits
     * @param parameters search parameters
     * @return handle for logging
     */
    SearchLogger addRequest(String corpus, String type, Map<String, String[]> parameters);

    /**
     * Log info about the state of the cache.
     *
     * @param snapshot save a snapshot of the cache contents?
     * @param numberOfSearches number of cached searches
     * @param numberRunning number of running searches
     * @param sizeBytes cache size in bytes
     * @param freeMemoryBytes free Java heap memory in bytes
     * @param largestEntryBytes largest cache entry
     * @param oldestEntryAgeSec oldest cache entry
     */
    void addCacheInfo(List<BlsCacheEntry<? extends SearchResult>> snapshot, int numberOfSearches, int numberRunning, long sizeBytes, long freeMemoryBytes,
            long largestEntryBytes, int oldestEntryAgeSec);

}
