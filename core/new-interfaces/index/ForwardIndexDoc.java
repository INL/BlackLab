package nl.inl.blacklab.interfaces.index;

import java.util.List;
import java.util.Map;

import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.interfaces.struct.Annotation;

/** Document in the (multi-)forward index. */
public interface ForwardIndexDoc {
	
	/**
	 * Gets the length (in tokens) of a document.
	 * @return length of the document
	 */
	int length();
	
	/**
	 * Retrieve entire document, in the form of token ids, for a single annotation.
	 * 
	 * @param annotation annotation to retrieve
	 * @return document tokens
	 */
	int[] retrieve(Annotation annotation);
	
	/**
	 * Retrieve part of this document, in the form of token ids.
	 *
	 * NOTE: if offset and length are both -1, retrieves the whole content. This is used by the
	 * retrieve() method.
	 *
	 * @param annotation annotation to retrieve
	 * @param start first token to retrieve (0 or -1 for first word)
	 * @param end first token after the last to retrieve (or -1 for end of document)
	 * @return tokens
	 */
	int[] retrievePart(Annotation annotation, int start, int end);
	
	/**
	 * Get a single token id from the forward index.
	 * 
	 * @param annotation annotation to retrieve
	 * @param pos position
	 * @return token id
	 */
	default int tokenAt(Annotation annotation, int pos) {
		// Slow/naive implementation, subclasses should provide a more efficient version
		return retrievePart(annotation, pos, pos + 1)[0];
	}

    /**
     * Store the given content.
     *
     * After calling this, call {@link ForwardIndex#store(ForwardIndexDocument)} to store the document.
     *
     * Note that if more than one token occurs at any position, we only store the first in the
     * forward index.
     *
     * @param content tokens for multiple annotations to store in the forward index
     * @param posIncr the associated position increments, or null if position increment is always 1.
     */
    void setContent(Map<Annotation, List<String>> content, IntArrayList posIncr);
	
	/**
	 * Delete document from the forward index
	 */
	void delete();
	
}