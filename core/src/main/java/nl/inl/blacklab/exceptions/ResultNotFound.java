package nl.inl.blacklab.exceptions;

public class ResultNotFound extends BlackLabException {

    public ResultNotFound(String message) {
        super(message);
    }

    public ResultNotFound(String message, Throwable e) {
        super(message, e);
    }
    
}
