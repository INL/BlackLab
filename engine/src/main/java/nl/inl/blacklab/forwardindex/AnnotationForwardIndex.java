package nl.inl.blacklab.forwardindex;

import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A forward index for a single annotation on a field.
 */
public interface AnnotationForwardIndex {

    void initialize();

    /**
     * Close the forward index. Writes the table of contents to disk if modified.
     */
    void close();

    /**
     * Store the given content and assign an id to it.
     *
     * Note that if more than one token occurs at any position, we only store the
     * first in the forward index.
     *
     * @param content the content to store
     * @param posIncr the associated position increments, or null if position
     *         increment is always 1.
     * @return the id assigned to the content
     */
    int addDocument(List<String> content, List<Integer> posIncr);

    /**
     * Store the given content and assign an id to it
     *
     * @param content the content to store
     * @return the id assigned to the content
     */
    int addDocument(List<String> content);

    /**
     * Delete a document from the forward index
     *
     * @param fiid id of the document to delete
     */
    void deleteDocument(int fiid);

    void deleteDocumentByLuceneDoc(Document d);

    /**
     * Retrieve one or more parts from the specified content, in the form of token
     * ids.
     *
     * This is more efficient than retrieving the whole content, or retrieving parts
     * in separate calls, because the file is only opened once and random access is
     * used to read only the required parts.
     *
     * NOTE: if offset and length are both -1, retrieves the whole content. This is
     * used by the retrieve(id) method.
     *
     * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an
     * OutOfMemoryError on the FileChannel.map() call (which makes no sense, because
     * memory mapping only uses address space, it doesn't try to read the whole
     * file). Possibly this could be solved by using 64-bit Java, but we haven't
     * tried. For now we just disable memory mapping on Windows.
     *
     * @param fiid forward index document id
     * @param start the starting points of the parts to retrieve (in words) (-1 for
     *         start of document)
     * @param end the end points (i.e. first token beyond) of the parts to retrieve
     *         (in words) (-1 for end of document)
     * @return the parts
     */
    List<int[]> retrievePartsInt(int fiid, int[] start, int[] end);

    /**
     * Retrieve token ids for the entire document.
     *
     * @param fiid forward index id
     * @return token ids for the entire document.
     */
    int[] getDocument(int fiid);

    /**
     * Get the Terms object in order to translate ids to token strings
     *
     * @return the Terms object
     */
    Terms terms();

    /**
     * @return the number of documents in the forward index
     */
    int numDocs();

    /**
     * @return the amount of space in free blocks in the forward index.
     */
    long freeSpace();

    /**
     * @return total size in bytes of the tokens file.
     */
    long totalSize();

    /**
     * Gets the length (in tokens) of a document.
     *
     * NOTE: this INCLUDES the extra closing token at the end of the document!
     *
     * @param fiid forward index id of a document
     * @return length of the document
     */
    int docLength(int fiid);

    int getToken(int fiid, int pos);

    /**
     * The annotation for which this is the forward index
     *
     * @return annotation
     */
    Annotation annotation();

    /** @return the set of all forward index ids */
    Set<Integer> idSet();

    boolean canDoNfaMatching();

    @Override
    String toString();

    /** Different versions of insensitive collator */
    public enum CollatorVersion {
        V1, // ignored dash and space
        V2 // doesn't ignore dash and space
    }

    /** A task to perform on a document in the forward index. */
    public interface ForwardIndexDocTask {
        void perform(int fiid, int[] tokenIds);
    }
}
