package nl.inl.blacklab.exceptions;

public class IndexVersionMismatch extends ErrorOpeningIndex {
    
    
    public IndexVersionMismatch(String message) {
        super(message);
    }
    
    public IndexVersionMismatch(String message, Throwable e) {
        super(message, e);
    }
    
    public IndexVersionMismatch(Throwable e) {
        super(e);
    }

}
