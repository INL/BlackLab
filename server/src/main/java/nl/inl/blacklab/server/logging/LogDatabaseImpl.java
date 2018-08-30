package nl.inl.blacklab.server.logging;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.LogException;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.requestlogging.SearchLogger;

public class LogDatabaseImpl implements Closeable, LogDatabase {

    private static final long FIVE_MIN_MS = 1000 * 60 * 5;
    
    private static final long THREE_MONTHS_MS = 3L * 31 * 24 * 3600 * 1000;

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String encodeEntryArray(Entry<String, String[]> entry) {
        return encode(entry.getKey()) + "=" + encode(StringUtils.join(entry.getValue(), "||"));
    }

    private static String encodeEntry(Entry<String, String> entry) {
        return encode(entry.getKey()) + "=" + encode(entry.getValue());
    }

    static String mapToQueryStringArray(Map<String, String[]> parameters) {
        return parameters.entrySet().stream().map(LogDatabaseImpl::encodeEntryArray).collect(Collectors.joining("&"));
    }

    static String mapToQueryString(Map<String, String> parameters) {
        return parameters.entrySet().stream().map(LogDatabaseImpl::encodeEntry).collect(Collectors.joining("&"));
    }
    
    static Map<String, String> queryStringToMap(String queryString) {
        Map<String, String> result = new HashMap<>();
        for (String keyValue: queryString.split("&")) {
            String[] parts = keyValue.split("=");
            result.put(decode(parts[0]), decode(parts[1]));
        }
        return result;
    }
    
    private Connection conn;
    
    public LogDatabaseImpl(String url) throws IOException {
        try {
            conn = DriverManager.getConnection(url);

            // Create the tables if they don't exist already
            execute("BEGIN TRANSACTION");
            try {
                execute(String.join("\n", 
                        "CREATE TABLE IF NOT EXISTS \"requests\" (",
                        "  `id`    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,",
                        "  `time`  INTEGER NOT NULL,",
                        "  `timestamp` TEXT NOT NULL,",
                        "  `corpus`    TEXT NOT NULL,",
                        "  `type`  TEXT NOT NULL,",
                        "  `parameters`    TEXT NOT NULL,",
                        "  `duration_ms`   INTEGER NOT NULL DEFAULT -1,",
                        "  `results_found` INTEGER NOT NULL DEFAULT -1",
                        ")"
                ));
                execute(String.join("\n", 
                        "CREATE TABLE IF NOT EXISTS \"request_logs\" (",
                        "  `id`    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,",
                        "  `time`  INTEGER NOT NULL,",
                        "  `timestamp` TEXT NOT NULL,",
                        "  `request`   INTEGER NOT NULL,",
                        "  `level` INTEGER DEFAULT 0,",
                        "  `line`  TEXT NOT NULL,",
                        "  FOREIGN KEY(`request`) REFERENCES requests ( id )",
                        ")"
                ));
                execute(String.join("\n", 
                        "CREATE TABLE IF NOT EXISTS \"cache_stats\" (",
                        "  `time`  INTEGER NOT NULL,",
                        "  `timestamp` TEXT NOT NULL,",
                        "  `num_searches`  INTEGER NOT NULL,",
                        "  `num_running`   INTEGER NOT NULL,",
                        "  `num_paused`    INTEGER NOT NULL,",
                        "  `size_bytes`    INTEGER NOT NULL,",
                        "  `free_mem_bytes`    INTEGER NOT NULL,",
                        "  `largest_entry_bytes`   INTEGER NOT NULL,",
                        "  `oldest_entry_sec`  INTEGER NOT NULL",
                        ")"
                ));
                
                // Don't let the database grow too large
                long threeMonthsAgo = now() - THREE_MONTHS_MS;
                execute("DELETE FROM request_logs WHERE time < " + threeMonthsAgo);
                execute("DELETE FROM requests WHERE time < " + threeMonthsAgo);
                execute("DELETE FROM cache_stats WHERE time < " + threeMonthsAgo);
                
                execute("COMMIT");
            } catch(SQLException e) {
                execute("ROLLBACK");
                throw new IOException(e);
            }
        
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
    
    private void execute(String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    @Override
    public List<Request> getRequests(long from, long to) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id, corpus, type, parameters, time, timestamp, duration_ms, results_found, size_bytes " +
                "FROM requests WHERE time >= ? AND time <= ? ORDER BY time")) {
            stmt.setLong(1, from);
            stmt.setLong(2, to);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Request> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new Request(this, rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new LogException(e);
        }
    }
    
    @Override
    public List<CacheStats> getCacheStats(long from, long to) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT time, timestamp, num_searches, num_running, num_paused, " +
                "size_bytes, free_mem_bytes, largest_entry_bytes, oldest_entry_sec " +
                "FROM cache_stats WHERE time >= ? AND time <= ? ORDER BY time")) {
            stmt.setLong(1, from);
            stmt.setLong(2, to);
            try (ResultSet rs = stmt.executeQuery()) {
                List<CacheStats> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new CacheStats(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new LogException(e);
        }
    }

    /**
     * Get log lines for a specific request.
     * 
     * @param id request id
     * @return log lines
     */
    List<SearchLogLine> getRequestLogLines(int id) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT request, time, timestamp, level, line FROM request_logs WHERE request = ? ORDER BY time")) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                List<SearchLogLine> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(new SearchLogLine(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new LogException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
    
    @Override
    public SearchLogger addRequest(String corpus, String type, Map<String, String[]> parameters) {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO requests (corpus, type, parameters, time, timestamp) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, corpus);
            stmt.setString(2, type);
            String strParameters = mapToQueryStringArray(parameters);
            stmt.setString(3, strParameters);
            long now = now();
            stmt.setLong(4, now);
            stmt.setString(5, timestamp(now));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new SearchLoggerImpl(this, rs.getInt(1));
                } else {
                    throw new LogException("Insert didn't generate an id!");
                }
            }
        } catch (SQLException e) {
            throw new LogException(e);
        }
    }

    void requestFinalize(int requestId, int resultsFound, long durationMs) {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE requests SET duration_ms = ?, results_found = ? WHERE id = ?")) {
            stmt.setLong(1, durationMs);
            stmt.setInt(2, resultsFound);
            stmt.setInt(3, requestId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new LogException(e);
        }
    }
    
    /**
     * Log a line for a request.
     * 
     * @param requestId request id this log line belongs to
     * @param line log line
     */
    void requestAddLogLine(int requestId, LogLevel level, String line) {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO request_logs (request, time, timestamp, level, line) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setInt(1, requestId);
            long now = now();
            stmt.setLong(2, now);
            stmt.setString(3, timestamp(now));
            int l = level.intValue();
            stmt.setInt(4, l);
            stmt.setString(5, StringUtils.repeat("    ", l - 1) + line);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new LogException(e);
        }
    }
    
    @Override
    public void addCacheInfo(int numberOfSearches, int numberRunning, int numberPaused, long sizeBytes, long freeMemoryBytes, long largestEntryBytes, int oldestEntryAgeSec) {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO cache_stats (time, timestamp, num_searches, num_running, num_paused, size_bytes, free_mem_bytes, " +
                "largest_entry_bytes, oldest_entry_sec) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            long now = now();
            stmt.setLong(1, now);
            stmt.setString(2, timestamp(now));
            stmt.setInt(3, numberOfSearches);
            stmt.setInt(4, numberRunning);
            stmt.setInt(5, numberPaused);
            stmt.setLong(6, sizeBytes);
            stmt.setLong(7, freeMemoryBytes);
            stmt.setLong(8, largestEntryBytes);
            stmt.setInt(9, oldestEntryAgeSec);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new LogException(e);
        }
    }

    static long now() {
        return System.currentTimeMillis();
    }

    static String timestamp(long time) {
        return new Timestamp(time).toString();
    }

    public static void main(String[] args) throws IOException {
        Random random = new Random();
        
        File dbFile = new File("/home/jan/blacklab/sqlite_log.db");
        String url = "jdbc:sqlite:" + dbFile.getCanonicalPath().replaceAll("\\\\", "/");
        try (LogDatabaseImpl log = new LogDatabaseImpl(url)) {
            
            // Add some requests
            for (int i = 0; i < 3; i++) {
                log.addCacheInfo(i, 1, 0, i * 1000, 10000 - (i*1000), i * 333, i * 100);
                System.out.println("Request " + i);
                Map<String, String[]> param = new HashMap<>();
                param.put("a", new String[] {"b"});
                param.put("c", new String[] {"d"});
                try (SearchLogger req = log.addRequest("opensonar", "hits", param)) {
                    req.log(LogLevel.BASIC, "Ga request " + req + " uitvoeren...");
                    Thread.sleep(300);
                    req.log(LogLevel.BASIC, "Bezig met request " + req + " uitvoeren...");
                    Thread.sleep(300);
                    req.log(LogLevel.BASIC, "request " + req + " klaar!");
                    int n = random.nextInt();
                    req.setResultsFound(n);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            
            // Select requests from last 5 min
            for (Request req: log.getRequests(now() - FIVE_MIN_MS, now())) {
                System.out.println("REQUEST: " + req);
                for (SearchLogLine line: req.logLines()) {
                    System.out.println("  " + line);
                }
            }
            
            // Select cache stats from last 5 min
            for (CacheStats stats: log.getCacheStats(now() - FIVE_MIN_MS, now())) {
                System.out.println("CACHE: " + stats);
            }
        }
    }
}
