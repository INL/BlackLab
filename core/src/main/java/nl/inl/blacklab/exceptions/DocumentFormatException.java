package nl.inl.blacklab.exceptions;

/**
 * An unknown document format was specified, or there was some other problem
 * with the document format.
 */
public class DocumentFormatException extends BlackLabException {
    public DocumentFormatException(String message) {
        super(message);
    }
}
