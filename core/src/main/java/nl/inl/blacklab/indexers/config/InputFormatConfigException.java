package nl.inl.blacklab.indexers.config;

import nl.inl.blacklab.search.BlackLabException;

/**
 * Thrown when there's an error in the input format configuration.
 */
public class InputFormatConfigException extends BlackLabException {

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
