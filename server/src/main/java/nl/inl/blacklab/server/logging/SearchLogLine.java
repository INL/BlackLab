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
    
    String line;
    
    SearchLogLine(ResultSet rs) throws SQLException {
        requestId = rs.getInt("request");
        time = rs.getLong("time");
        timestamp = rs.getString("timestamp");
        line = rs.getString("line");
    }
    
    @Override
    public String toString() {
        return timestamp + " " + line;
    }
    
}