package nl.inl.blacklab.exceptions;

/**
 * Thrown when there's an error in the input format configuration.
 */
public class InvalidInputFormatConfig extends BlackLabRuntimeException {

    public InvalidInputFormatConfig(String message) {
        super(message);
    }

    public InvalidInputFormatConfig(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidInputFormatConfig(Throwable cause) {
        super(cause);
    }

}
