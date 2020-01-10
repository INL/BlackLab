package nl.inl.blacklab.requestlogging;

public enum LogLevel {
    BASIC(1),   // basic information about query execution, e.g. what was found in cache and what not
    EXPLAIN(2), // explanation of how the query was executed, e.g. rewrite results
    OPT(3),     // more information about optimizations, such as why certain clauses were combined
    DETAIL(4),  // a lot of detail, such as exact priority values calculated during optimization
    CHATTY(5);  // more detail than you would likely want
    
    public static LogLevel fromIntValue(int i) {
        for (LogLevel  v: values()) {
            if (v.intValue() == i)
                return v;
        }
        return BASIC;
    }
    
    int i;
    
    LogLevel(int i) {
        this.i = i;
    }
    
    public int intValue() {
        return i;
    }
}