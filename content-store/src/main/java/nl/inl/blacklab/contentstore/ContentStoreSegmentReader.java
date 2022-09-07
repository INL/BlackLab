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

    String getValue(int docId, String luceneField);

    String getValueSubstring(int docId, String luceneField, int start, int end);

    String[] getValueSubstrings(int docId, String luceneField, int[] start, int[] end);

    int valueLength(int docId, String luceneField);
}
