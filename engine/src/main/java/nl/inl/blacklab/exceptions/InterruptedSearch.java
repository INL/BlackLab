package nl.inl.blacklab.exceptions;

/**
 * Thrown in response to InterruptedException to indicate that a thread was interrupted.
 * 
 * E.g. BlackLab Search aborts searches that run for too long, causing this exception 
 * to be thrown.
 */
public class InterruptedSearch extends BlackLabRuntimeException {

    public InterruptedSearch() {
        super();
    }
    
    public InterruptedSearch(InterruptedException e) {
        super(e);
    }

    public InterruptedSearch(String message) {
        super(message);
    }
    
    public InterruptedSearch(String message, InterruptedException e) {
        super(message, e);
    }
    
}
