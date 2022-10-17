package nl.inl.blacklab.server.exceptions;

/**
 * Thrown when the requested index was not available or could not be opened
 */
public class BlsIndexOpenException extends InternalServerError {

    public BlsIndexOpenException(String errorCode, String msg, Throwable cause) {
        super(errorCode, msg, cause);
    }

    public BlsIndexOpenException(String errorCode, String msg) {
        this(errorCode, msg, null);
    }

}
