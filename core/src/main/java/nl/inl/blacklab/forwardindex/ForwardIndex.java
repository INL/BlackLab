package nl.inl.blacklab.forwardindex;

import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A component that can quickly tell you what word occurs at a specific position
 * of a specific document.
 * 
 * This allows access to all annotations for a document, as opposed to {@link AnnotationForwardIndex},
 * which allows access to just one.
 */
public interface ForwardIndex extends Iterable<AnnotationForwardIndex> {

    /**
     * Open a forward index.
     *
     * Automatically figures out the forward index version and instantiates the
     * right class.
     * 
     * @param index our index 
     * @param field field for which this is the forward index
     * @return the forward index object
     */
    static ForwardIndex open(BlackLabIndex index, AnnotatedField field) {
        return new ForwardIndexImplSeparate(index, field);
    }

    /**
     * Get a document from the forward index.
     * 
     * @param fiid forward index id
     * @return forward index document
     */
    FIDoc doc(int fiid);
    
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
     *            increment is always 1.
     * @param currentLuceneDoc Lucene document
     */
    void addDocument(Map<Annotation, List<String>> content, Map<Annotation, List<Integer>> posIncr, Document currentLuceneDoc);

    /**
     * Get the Terms object in order to translate ids to token strings
     * 
     * @param annotation annotation to get terms for 
     * @return the Terms object
     */
    Terms terms(Annotation annotation);

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
     * The field for which this is the forward index
     * 
     * @return field
     */
    AnnotatedField field();

    /**
     * Get a single-annotation view of the forward index.
     * 
     * @param annotation annotation to get a view of
     * @return single-annotation forward index view
     */
    AnnotationForwardIndex get(Annotation annotation);

    /**
     * Add a forward index.
     * 
     * @param annotation annotation for which this is the forward index
     * @param forwardIndex forward index to add
     */
    void put(Annotation annotation, AnnotationForwardIndex forwardIndex);

    boolean hasAnyForwardIndices();

    boolean canDoNfaMatching();

}
