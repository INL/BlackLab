package nl.inl.blacklab.server.logging;

import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.requestlogging.SearchLogger;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.server.search.BlsCacheEntry;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ConsoleLogDatabase provides an implementation to LogDatabase, that simply
 * logs internal blacklab operations to stdout.
 * ConsoleLogDatabase is a lightweight tracing class to enable debugging of search requests.
 * This class logs in the trace level. To see its output set the loglevel to trace.
 */
public class ConsoleLogDatabase implements LogDatabase {
    private static final Logger LOGGER = LogManager.getLogger(ConsoleLogDatabase.class);

    @Override
    public List<Request> getRequests(long from, long to) {
        return null;
    }

    @Override
    public List<CacheStats> getCacheStats(long from, long to) {
        return null;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public SearchLogger addRequest(String corpus, String type, Map<String, String[]> parameters) {
        return new SearchLogger() {
            @Override
            public void log(LogLevel level, String line) {
                ConsoleLogDatabase.LOGGER.trace(line);
            }

            @Override
            public void setResultsFound(int resultsFound) {

            }

            @Override
            public void close() throws IOException {

            }
        };
    }

    @Override
    public void addCacheInfo(List<BlsCacheEntry<? extends SearchResult>> snapshot, int numberOfSearches, int numberRunning, int numberPaused, long sizeBytes, long freeMemoryBytes, long largestEntryBytes, int oldestEntryAgeSec) {

    }
}
