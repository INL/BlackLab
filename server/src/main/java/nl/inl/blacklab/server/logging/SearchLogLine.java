package nl.inl.blacklab.server.logging;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Log line for a request.
 */
public class SearchLogLine {
    
    int requestId;
    
    long time;
    
    String timestamp;
    
    int level;
    
    String line;
    
    SearchLogLine(ResultSet rs) throws SQLException {
        requestId = rs.getInt("request");
        time = rs.getLong("time");
        timestamp = rs.getString("timestamp");
        level = rs.getInt("level");
        line = rs.getString("line");
    }
    
    @Override
    public String toString() {
        return timestamp + " [" + level + "] " + line;
    }
    
}