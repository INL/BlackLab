package nl.inl.blacklab.contentstore;

import net.jcip.annotations.NotThreadSafe;

/**
 * Provides read access to the content stores in a single Lucene index segment.
 *
 * Implementations are not intended to be threadsafe, but to be used by a single
 * thread.
 */
@NotThreadSafe
public interface ContentStoreSegmentReader {

    /**
     * Get the entire field value.
     *
     * @param docId document id
     * @param luceneField field to get
     * @return field value
     */
    byte[] getBytes(int docId, String luceneField);

    /**
     * Get the entire field value.
     *
     * @param docId document id
     * @param luceneField field to get
     * @return field value
     */
    String getValue(int docId, String luceneField);

    /**
     * Get part of the field value.
     *
     * @param docId document id
     * @param luceneField field to get
     * @param startChar first character to get. Must be zero or greater.
     * @param endChar character after the last character to get, or -1 for <code>value.length()</code>.
     * @return requested part
     */
    String getValueSubstring(int docId, String luceneField, int start, int end);

    /**
     * Get several parts of the field value.
     *
     * @param docId document id
     * @param luceneField field to get
     * @param star positions of the first character to get. Must all be zero or greater.
     * @param end positions of the character after the last character to get, or -1 for <code>value.length()</code>.
     * @return requested parts
     */
    String[] getValueSubstrings(int docId, String luceneField, int[] start, int[] end);

    /**
     * Finds the length in characters of a stored value.
     *
     * Also positions the valueIndexFile pointer to just after the doc length,
     * from which we can continue reading information about the value (such as where
     * to find the actual value itself).
     *
     * @param docId document id
     * @param luceneField field to get length for
     * @return length of the value in characters
     */
    int valueLength(int docId, String luceneField);
}
