package nl.inl.blacklab.search;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.search.indexmetadata.Field;

/**
 * Defines a way to access the original indexed content.
 */
public class ContentAccessor {
    protected String fieldName;

    private ContentStore contentStore;

    private String contentIdField = null;

    public ContentAccessor(Field field, ContentStore contentStore) {
        contentIdField = field.contentIdField();
        this.contentStore = contentStore;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ContentStore getContentStore() {
        return contentStore;
    }

    public ContentAccessor(String fieldName) {
        this.fieldName = fieldName;
    }

    private int getContentId(int docId, Document d) {
        return contentStore.getContentId(docId, d, contentIdField);
    }

    /**
     * Get substrings from a document.
     *
     * Note: if start and end are both -1 for a certain substring, the whole
     * document is returned.
     *
     * @param d the Lucene document (contains the file name)
     * @param start start positions of the substrings. -1 means start of document.
     * @param end end positions of the substrings. -1 means end of document.
     * @return the requested substrings from this document
     */
    public String[] getSubstringsFromDocument(int docId, Document d, int[] start, int[] end) {
        return getSubstringsFromDocument(getContentId(docId, d), start, end);
    }

    /**
     * Get substrings from a document.
     *
     * Note: if start and end are both -1 for a certain substring, the whole
     * document is returned.
     *
     * @param contentId the content id
     * @param start start positions of the substrings. -1 means start of document.
     * @param end end positions of the substrings. -1 means end of document.
     * @return the requested substrings from this document
     */
    public String[] getSubstringsFromDocument(int contentId, int[] start, int[] end) {
        return contentStore.retrieveParts(contentId, start, end);
    }

    public void delete(Document d) {
        delete(getContentId(-1, d));
    }

    private void delete(int contentId) {
        // (NOTE: this will throw if we're an integrated index)
        contentStore.delete(contentId);
    }

    public void close() {
        contentStore.close();
    }

}
