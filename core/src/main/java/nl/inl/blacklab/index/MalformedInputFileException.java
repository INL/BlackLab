package nl.inl.blacklab.index;

import nl.inl.blacklab.search.BLRuntimeException;

/**
 * Thrown when the file you're indexing is malformed in some way
 * (i.e. not well-formed XML) 
 */
public class MalformedInputFileException extends BLRuntimeException {

    public MalformedInputFileException() {
        super();
    }

    public MalformedInputFileException(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedInputFileException(String message) {
        super(message);
    }

    public MalformedInputFileException(Throwable cause) {
        super(cause);
    }

}
