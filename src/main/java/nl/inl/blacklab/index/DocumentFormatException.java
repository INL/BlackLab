package nl.inl.blacklab.index;

/**
 * An unknown document format was specified, or there
 * was some other problem with the document format.
 */
public class DocumentFormatException extends Exception {
	public DocumentFormatException(String message) {
		super(message);
	}
}
