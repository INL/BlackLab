package nl.inl.blacklab.forwardindex;

import java.text.Collator;
import java.util.List;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.LeafReaderLookup;
import nl.inl.blacklab.forwardindex.Collators.CollatorVersion;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

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
    public static AnnotationForwardIndex open(IndexReader reader, Annotation annotation, Collator collator) {
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation doesn't have a forward index: " + annotation);

        Collators collators = new Collators(collator, CollatorVersion.V2);
        return new AnnotationForwardIndexIntegrated(reader, annotation, collators);
    }

    private final IndexReader indexReader;

    private final Annotation annotation;

    /** The Lucene field that contains our forward index */
    private final String luceneField;

    /** Collators to use for comparisons */
    private final Collators collators;

    private final Terms terms;

    /** Index of segments by their doc base (the number to add to get global docId) */
    private final LeafReaderLookup leafReaderLookup;

    public AnnotationForwardIndexIntegrated(IndexReader indexReader, Annotation annotation, Collators collators) {
        super();
        this.indexReader = indexReader;
        this.annotation = annotation;
        this.collators = collators;
        AnnotationSensitivity annotSens = annotation.hasSensitivity(
                MatchSensitivity.SENSITIVE) ?
                annotation.sensitivity(MatchSensitivity.SENSITIVE) :
                annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        this.luceneField = annotSens.luceneField();

        // Ensure quick lookup of the segment we need
        leafReaderLookup = new LeafReaderLookup(indexReader);

        terms = new TermsIntegrated(collators, indexReader, luceneField);
    }

    @Override
    public void initialize() {
        // ...
    }

    @Override
    public Terms terms() {
        return terms;
    }

    @Override
    public List<int[]> retrievePartsInt(int docId, int[] start, int[] end) {
        LeafReaderContext lrc = leafReaderLookup.forDocId(docId);
        ForwardIndexSegmentReader fi = BlackLabIndexIntegrated.forwardIndex(lrc);
        List<int[]> segmentResults = fi.retrieveParts(luceneField, docId - lrc.docBase, start, end);
        return terms.segmentIdsToGlobalIds(lrc.ord, segmentResults);
    }

    @Override
    public int docLength(int docId) {
        LeafReaderContext lrc = leafReaderLookup.forDocId(docId);
        ForwardIndexSegmentReader fi = BlackLabIndexIntegrated.forwardIndex(lrc);
        return (int)fi.docLength(luceneField, docId - lrc.docBase);
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public Collators collators() {
        return collators;
    }

    @Override
    public boolean canDoNfaMatching() {
        return true; // depends on collator version, and integrated always uses V2
    }

    @Override
    public int numDocs() {
        return indexReader.numDocs();
    }
}
