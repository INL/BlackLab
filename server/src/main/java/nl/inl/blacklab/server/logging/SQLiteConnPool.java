package nl.inl.blacklab.server.logging;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.dbcp.BasicDataSource;

public class SQLiteConnPool implements Closeable {
    
    private BasicDataSource ds;
     
    public SQLiteConnPool(String url) {
        ds = new BasicDataSource();
        ds.setUrl(url);
        ds.setMinIdle(5);
        ds.setMaxIdle(10);
        ds.setMaxOpenPreparedStatements(100);
    }
     
    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    @Override
    public void close() throws IOException {
        try {
            ds.close();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
