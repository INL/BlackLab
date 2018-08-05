package nl.inl.blacklab.interfaces.index;

/** Single-annotation view of a document in the forward index. */
public interface AnnotationForwardIndexDoc {
	
	/**
	 * Gets the length (in tokens) of a document.
	 * @return length of the document
	 */
	int length();
	
	/**
	 * Retrieve entire document, in the form of token ids.
	 *
	 * @return document tokens
	 */
	int[] retrieve();
	
	/**
	 * Retrieve part of this document, in the form of token ids.
	 *
	 * NOTE: if offset and length are both -1, retrieves the whole content. This is used by the
	 * retrieve() method.
	 *
	 * @param start first token to retrieve (0 or -1 for first word)
	 * @param end first token after the last to retrieve (or -1 for end of document)
	 * @return tokens
	 */
	int[] retrievePart(int start, int end);
	
	/**
	 * Get a single token id from the forward index.
	 * 
	 * @param pos position
	 * @return token id
	 */
	default int tokenAt(int pos) {
		// Slow/naive implementation, subclasses should provide a more efficient version
		return retrievePart(pos, pos + 1)[0];
	}
	
}