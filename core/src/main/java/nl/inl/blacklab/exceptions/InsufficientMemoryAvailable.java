package nl.inl.blacklab.exceptions;

/**
 * Not enough memory available to perform the requested operation.
 */
public class InsufficientMemoryAvailable extends BlackLabRuntimeException {
    
    public InsufficientMemoryAvailable(String msg) {
        super(msg);
    }
}
