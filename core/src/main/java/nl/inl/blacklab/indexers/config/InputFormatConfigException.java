package nl.inl.blacklab.indexers.config;

import nl.inl.blacklab.search.BLRuntimeException;

/**
 * Thrown when there's an error in the input format configuration.
 */
public class InputFormatConfigException extends BLRuntimeException {

    public InputFormatConfigException(String message) {
        super(message);
    }

    public InputFormatConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public InputFormatConfigException(Throwable cause) {
        super(cause);
    }

}
