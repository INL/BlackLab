package nl.inl.blacklab.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Field;

/**
 * Defines a way to access the original indexed content.
 */
public class ContentAccessor {
    protected Field field;

    private ContentStore contentStore;

    private String contentIdField;

    public ContentAccessor(Field field, ContentStore contentStore) {
        this.field = field;
        contentIdField = field.contentIdField();
        this.contentStore = contentStore;
    }

    /**
     * Get the entire document contents.
     *
     * This takes into account parallel corpora, where one of the annotated fields stores all the versions
     * of the original document, and we keep track of the start/end offsets for each version.
     *
     * @param annotatedFieldName annotated field name, e.g. "contents" or "contents__nl" (might be different from this
     *                           ContentAccessor's field because parallel corpora store entire file with all versions in
     *                           the main annotated field)
     * @param docId the Lucene document id
     * @param doc the Lucene document
     * @return the entire document contents
     */
    public String getDocumentContents(String annotatedFieldName, int docId, Document doc) {
        int start = -1, end = -1;
        IndexableField startOffset = doc.getField(AnnotatedFieldNameUtil.docStartOffsetField(annotatedFieldName));
        if (startOffset != null) {
            // We actually want part of the original input document (usually one version in a parallel corpus)
            // get the start/end offsets
            start = startOffset.numericValue().intValue();
            String endOffsetField = AnnotatedFieldNameUtil.docEndOffsetField(annotatedFieldName);
            end = doc.getField(endOffsetField).numericValue().intValue();
        }
        return getSubstringsFromDocument(docId, doc, new int[] { start }, new int[] { end })[0];
    }

    public Field getField() {
        return field;
    }

    public ContentStore getContentStore() {
        return contentStore;
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
