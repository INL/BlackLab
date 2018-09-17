package nl.inl.blacklab.server.logging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * A request.
 */
public class Request {
    
    private static String encodeEntry(Entry<String, String> entry) {
        return LogDatabaseImpl.encode(entry.getKey()) + "=" + LogDatabaseImpl.encode(entry.getValue());
    }
    
    static String mapToQueryString(Map<String, String> parameters) {
        return parameters.entrySet().stream().map(Request::encodeEntry).collect(Collectors.joining("&"));
    }
    
    private final LogDatabaseImpl logDatabase;

    private int id;
    
    private String corpus;
    
    private String type;
    
    private Map<String, String> parameters;
    
    private long time;
    
    private String timestamp;
    
    private long durationMs;
    
    private int resultsFound;
    
    private long sizeBytes;

    Request(LogDatabaseImpl logDatabase, ResultSet rs) throws SQLException {
        this.logDatabase = logDatabase;
        id = rs.getInt("id");
        corpus = rs.getString("corpus");
        type = rs.getString("type");
        parameters = LogDatabaseImpl.queryStringToMap(rs.getString("parameters"));
        time = rs.getLong("time");
        timestamp = rs.getString("timestamp");
        durationMs = rs.getLong("duration_ms");
        resultsFound = rs.getInt("results_found");
        sizeBytes = rs.getLong("size_bytes");
    }
    
    public int getId() {
        return id;
    }

    public String getCorpus() {
        return corpus;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public long getTime() {
        return time;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public int getResultsFound() {
        return resultsFound;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    List<SearchLogLine> logLines() {
        return this.logDatabase.getRequestLogLines(id);
    }
    
    @Override
    public String toString() {
        return timestamp + " /" + corpus + "/" + type + "?" + mapToQueryString(parameters);
    }
}