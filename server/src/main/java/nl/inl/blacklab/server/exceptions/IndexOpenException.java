package nl.inl.blacklab.server.exceptions;

import java.io.IOException;

/**
 * Thrown when the requested index was not available or could not be opened
 */
public class IndexOpenException extends IOException {

    public IndexOpenException() {
        super();
    }

    public IndexOpenException(String msg) {
        super(msg);
    }

    public IndexOpenException(Throwable cause) {
        super(cause);
    }

    public IndexOpenException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
