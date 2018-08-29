package nl.inl.blacklab.server.logging;

import java.io.IOException;

import nl.inl.blacklab.requestlogging.SearchLogger;

/**
 * Handle used to log information about a search.
 */
public class SearchLoggerImpl implements SearchLogger {
    
    private final LogDatabaseImpl logDatabase;

    private int id;
    
    private long startedAt;
    
    private int resultsFound = -1;

    SearchLoggerImpl(LogDatabaseImpl logDatabase, int requestId) {
        this.logDatabase = logDatabase;
        this.id = requestId;
        this.startedAt = System.currentTimeMillis();
    }

    @Override
    public void log(String line) {
        this.logDatabase.requestAddLogLine(id, line);
    }
    
    @Override
    public void setResultsFound(int resultsFound) {
        this.resultsFound = resultsFound;
    }

    @Override
    public void close() throws IOException {
        this.logDatabase.requestFinalize(id, resultsFound, System.currentTimeMillis() - startedAt);
    }

    public int id() {
        return id;
    }
    
    @Override
    public String toString() {
        return "#" + id;
    }
    
}