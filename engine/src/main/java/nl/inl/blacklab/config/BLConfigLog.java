package nl.inl.blacklab.config;

public class BLConfigLog {
    BLConfigTrace trace = new BLConfigTrace();
    
    public BLConfigTrace getTrace() {
        return trace;
    }

    public void setTrace(BLConfigTrace trace) {
        this.trace = trace;
    }
    
}