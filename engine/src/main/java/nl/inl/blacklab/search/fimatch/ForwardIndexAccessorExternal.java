package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 */
@ThreadSafe
public class ForwardIndexAccessorExternal extends ForwardIndexAccessorAbstract {

    /** The forward index for each annotation */
    private final List<AnnotationForwardIndex> fis = new ArrayList<>();

    public ForwardIndexAccessorExternal(BlackLabIndex index, AnnotatedField searchField) {
        super(index, searchField);
    }

    @Override
    protected synchronized int getAnnotationNumber(Annotation annotation) {
        fis.add(index.annotationForwardIndex(annotation));
        return super.getAnnotationNumber(annotation);
    }

    @Override
    public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext) {
        return new ForwardIndexAccessorLeafReaderExternal(readerContext);
    }

    /**
     * Forward index accessor for a single LeafReader.
     *
     * Not thread-safe (only used from Spans).
     */
    @NotThreadSafe
    class ForwardIndexAccessorLeafReaderExternal implements ForwardIndexAccessorLeafReader {

        protected final LeafReaderContext readerContext;

        ForwardIndexAccessorLeafReaderExternal(LeafReaderContext readerContext) {
            this.readerContext = readerContext;
        }

        @Override
        public ForwardIndexDocument advanceForwardIndexDoc(int docId) {
            return new ForwardIndexDocumentImpl(this, docId);
        }

        @Override
        public int getDocLength(int docId) {
            // NOTE: we subtract one because we always have an "extra closing token" at the end that doesn't
            //       represent a word, just any closing punctuation after the last word.
            return fis.get(0).docLength(docId + readerContext.docBase) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }

        final int[] starts = { 0 };
        final int[] ends = { 0 };

        @Override
        public int[] getChunk(int annotIndex, int docId, int start, int end) {
            starts[0] = start;
            ends[0] = end;
            return fis.get(annotIndex).retrievePartsInt(docId + readerContext.docBase, starts, ends).get(0);
        }

        @Override
        public int getNumberOfAnnotations() {
            return ForwardIndexAccessorExternal.this.numberOfAnnotations();
        }

        @Override
        public String getTermString(int annotIndex, int segmentTermId) {
            return ForwardIndexAccessorExternal.this.getTermString(annotIndex, segmentTermId);
        }

        @Override
        public boolean termsEqual(int annotIndex, int[] segmentTermIds, MatchSensitivity sensitivity) {
            return ForwardIndexAccessorExternal.this.termsEqual(annotIndex, segmentTermIds, sensitivity);
        }
    }
}
