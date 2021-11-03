package nl.inl.blacklab.server.logging;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CacheStats {
    private long time;

    private String timestamp;

    private int numSearches;

    private int numRunning;

    private long sizeBytes;

    private long freeMemBytes;

    private long largestEntryBytes;

    private long oldestEntrySec;

    CacheStats(ResultSet rs) throws SQLException {
        time = rs.getLong("time");
        timestamp = rs.getString("timestamp");
        numSearches = rs.getInt("num_searches");
        numRunning = rs.getInt("num_running");
        sizeBytes = rs.getLong("size_bytes");
        freeMemBytes = rs.getLong("free_mem_bytes");
        largestEntryBytes = rs.getLong("largest_entry_bytes");
        oldestEntrySec = rs.getLong("oldest_entry_sec");
    }

    public long getTime() {
        return time;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getNumSearches() {
        return numSearches;
    }

    public int getNumRunning() {
        return numRunning;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getFreeMemBytes() {
        return freeMemBytes;
    }

    public long getLargestEntryBytes() {
        return largestEntryBytes;
    }

    public long getOldestEntrySec() {
        return oldestEntrySec;
    }

    @Override
    public String toString() {
        return "CacheStats [timestamp=" + timestamp + ", numSearches=" + numSearches + ", numRunning=" + numRunning
                + ", sizeBytes=" + sizeBytes + ", freeMemBytes=" + freeMemBytes + "]";
    }

}