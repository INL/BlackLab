package nl.inl.blacklab.server.logging;

import java.io.IOException;

import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.requestlogging.SearchLogger;

/**
 * Handle used to log information about a search.
 */
public class SearchLoggerImpl implements SearchLogger {
    
    private final LogDatabaseImpl logDatabase;

    private int id;
    
    private long startedAt;
    
    private int resultsFound = -1;
    
    private boolean isClosed = false;

    SearchLoggerImpl(LogDatabaseImpl logDatabase, int requestId) {
        this.logDatabase = logDatabase;
        this.id = requestId;
        this.startedAt = System.currentTimeMillis();
    }

    @Override
    public void log(LogLevel level, String line) {
        if (!isClosed)
            this.logDatabase.requestAddLogLine(id, level, line);
    }
    
    @Override
    public void setResultsFound(int resultsFound) {
        this.resultsFound = resultsFound;
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
        this.logDatabase.requestFinalize(id, resultsFound, System.currentTimeMillis() - startedAt);
    }

    public int id() {
        return id;
    }
    
    @Override
    public String toString() {
        return "#" + id + (isClosed ? " (CLOSED)" : "");
    }
    
}