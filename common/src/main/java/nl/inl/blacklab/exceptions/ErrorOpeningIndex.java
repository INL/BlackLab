package nl.inl.blacklab.exceptions;

public class ErrorOpeningIndex extends BlackLabRuntimeException {
    
    public ErrorOpeningIndex(String message) {
        super(message);
    }
    
    public ErrorOpeningIndex(String message, Throwable e) {
        super(message, e);
    }
    
    public ErrorOpeningIndex(Throwable e) {
        super(e);
    }

}
