package nl.inl.blacklab.config;

public class BLConfigLog {
    BLConfigTrace trace = new BLConfigTrace();
    
    // (only applies to BLS, not BL in general)
    String sqliteDatabase = null;

    public String getSqliteDatabase() {
        return sqliteDatabase;
    }

    public void setSqliteDatabase(String sqliteDatabase) {
        this.sqliteDatabase = sqliteDatabase;
    }

    public BLConfigTrace getTrace() {
        return trace;
    }

    public void setTrace(BLConfigTrace trace) {
        this.trace = trace;
    }
    
}