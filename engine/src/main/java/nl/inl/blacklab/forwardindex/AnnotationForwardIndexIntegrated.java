package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Forward index for single annotation (FIs integrated).
 *
 * This implementation works with FIs integrated into the Lucene index.
 *
 * Note that in the integrated case, there's no separate forward index id (fiid),
 * but instead the Lucene docId is used.
 */
public class AnnotationForwardIndexIntegrated implements AnnotationForwardIndex {

    /**
     * Open an integrated forward index.
     *
     * @param annotation annotation for which we want to open the forward index
     * @param collator collator to use
     * @return forward index
     */
    public static AnnotationForwardIndex open(Annotation annotation, Collator collator) {
        if (annotation != null && !annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);

        Collators collators = new Collators(collator, CollatorVersion.V2);
        return new AnnotationForwardIndexIntegrated(annotation, collators);
    }

    private final Annotation annotation;

    private final Collators collators;

    public AnnotationForwardIndexIntegrated(Annotation annotation, Collators collators) {
        super();
        this.annotation = annotation;
        this.collators = collators;
    }

    @Override
    public void initialize() {
        // Handled by Lucene
    }

    @Override
    public void close() {
        // Handled by Lucene
    }

    @Override
    public List<int[]> retrievePartsInt(int docId, int[] start, int[] end) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public int[] getDocument(int docId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Terms terms() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public int docLength(int docID) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public int getToken(int docId, int pos) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public boolean canDoNfaMatching() {
        return true; // depends on collator version, and integrated always uses V2
    }

    @Override
    public int addDocument(List<String> content, List<Integer> posIncr) {
        throw new UnsupportedOperationException("Handled by Lucene");
    }

    @Override
    public int addDocument(List<String> content) {
        throw new UnsupportedOperationException("Handled by Lucene");
    }

    @Override
    public int numDocs() {
        throw new UnsupportedOperationException("Handled by Lucene");
    }

    @Override
    public long freeSpace() {
        throw new UnsupportedOperationException("Handled by Lucene");
    }

    @Override
    public long totalSize() {
        throw new UnsupportedOperationException("Handled by Lucene");
    }

    @Override
    public void deleteDocument(int docId) {
        // Handled by Lucene
    }

    @Override
    public void deleteDocumentByLuceneDoc(Document d) {
        // Handled by Lucene
    }

    @Override
    public Set<Integer> idSet() {
        throw new UnsupportedOperationException("Handled by Lucene");
    }
}
