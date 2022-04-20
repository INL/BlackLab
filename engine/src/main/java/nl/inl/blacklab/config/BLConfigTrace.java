package nl.inl.blacklab.config;

public class BLConfigTrace {
    boolean indexOpening = false;
    
    boolean optimization = false;
    
    boolean queryExecution = false;
    
    /** Cache operations (BLS only) */
    boolean cache = false;

    public boolean isIndexOpening() {
        return indexOpening;
    }

    @SuppressWarnings("unused")
    public void setIndexOpening(boolean traceIndexOpening) {
        this.indexOpening = traceIndexOpening;
    }

    public boolean isOptimization() {
        return optimization;
    }

    public boolean isQueryExecution() {
        return queryExecution;
    }

    @SuppressWarnings("unused")
    public void setQueryExecution(boolean queryExecution) {
        this.queryExecution = queryExecution;
    }

    @SuppressWarnings("unused")
    public void setOptimization(boolean optimization) {
        this.optimization = optimization;
    }

    public boolean isCache() {
        return cache;
    }

    @SuppressWarnings("unused")
    public void setCache(boolean cache) {
        this.cache = cache;
    }
    
}
