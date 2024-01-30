package nl.inl.blacklab.contentstore;

import org.apache.lucene.document.Document;

public interface ContentStore {
    /**
     * Retrieve a document from the content store.
     *
     * @param id document id
     * @return the original content
     */
    default String retrieve(int id) {
        String[] rv = retrieveParts(id, new int[] { -1 }, new int[] { -1 });
        return rv == null ? null : rv[0];
    }

    /**
     * Retrieve one or more substrings from the specified content.
     * This is more efficient than retrieving the whole content, or retrieving parts
     * in separate calls, because the file is only opened once and random access is
     * used to read only the required parts.
     * NOTE: if offset and length are both -1, retrieves the whole content. This is
     * used by the retrieve(id) method.
     *
     * @param id    document id
     * @param start the starting points of the substrings (in characters). -1 means
     *              "start of document"
     * @param end   the end points of the substrings (in characters). -1 means "end of
     *              document"
     * @return the parts
     */
    default String retrievePart(int id, int start, int end) {
        return retrieveParts(id, new int[] { start }, new int[] { end })[0];
    }

    /**
     * Retrieve substrings from a document.
     *
     * @param id    document id
     * @param start start of the substring
     * @param end   end of the substring
     * @return the substrings
     */
    String[] retrieveParts(int id, int[] start, int[] end);

    /**
     * Close the content store
     */
    void close();

    /**
     * Returns the document length in characters
     *
     * @param id document id
     * @return the length in characters
     */
    int docLength(int id);

    /** Initialize the content store. May be run in a background thread. */
    void initialize();

    /**
     * Get the content id for the specified document.
     *
     * For modern indexes, just returns docId unchanged, as the content is
     * stored in the Lucene index. For older indexes, the content is stored
     * in a separate file, and the content id is stored in the Lucene index.
     *
     * @param docId Lucene doc id
     * @param d Lucene document
     * @param contentIdField  name of the field containing the content id (if applicable)
     * @return the content id
     */
    default int getContentId(int docId, Document d, String contentIdField) {
        // Modern integrated index format. Content is stored in the segment by Lucene docId.
        return docId;
    }

    /**
     * Delete a document from the content store.
     *
     * @param id content store id of the document to delete
     */
    default void delete(int id) {
        throw new UnsupportedOperationException("Cannot delete from content store in integrated index format");
    }
}
