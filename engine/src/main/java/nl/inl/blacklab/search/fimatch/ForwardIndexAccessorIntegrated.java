package nl.inl.blacklab.search.fimatch;

import java.util.List;
import java.util.Objects;

import org.apache.lucene.index.LeafReaderContext;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.codec.BLFieldsProducer;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.DocFieldLengthGetter;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 */
@ThreadSafe
public class ForwardIndexAccessorIntegrated extends ForwardIndexAccessorAbstract {

    public ForwardIndexAccessorIntegrated(BlackLabIndex index, AnnotatedField searchField) {
        super(index, searchField);
    }

    @Override
    public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext) {
        return new ForwardIndexAccessorLeafReaderIntegrated(readerContext);
    }

    /**
     * Forward index accessor for a single LeafReader.
     *
     * Not thread-safe (only used from Spans).
     */
    @NotThreadSafe
    class ForwardIndexAccessorLeafReaderIntegrated implements ForwardIndexAccessorLeafReader {

        protected final LeafReaderContext readerContext;

        private final ForwardIndexSegmentReader forwardIndexReader;

        private final DocFieldLengthGetter lengthGetter;

        ForwardIndexAccessorLeafReaderIntegrated(LeafReaderContext readerContext) {
            this.readerContext = readerContext;
            BLFieldsProducer fieldsProducer = BLFieldsProducer.get(readerContext, annotatedField.tokenLengthField());
            forwardIndexReader = fieldsProducer.forwardIndex();
            lengthGetter = new DocFieldLengthGetter(readerContext.reader(), annotatedField.name());
        }

        @Override
        public ForwardIndexDocument advanceForwardIndexDoc(int docId) {
            return new ForwardIndexDocumentImpl(this, docId);
        }

        @Override
        public int getDocLength(int docId) {
            // NOTE: we subtract one because we always have an "extra closing token" at the end that doesn't
            //       represent a word, just any closing punctuation after the last word.
            return lengthGetter.getFieldLength(docId)
                    - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }

        @Override
        public int[] getChunk(int annotIndex, int docId, int start, int end) {
            Annotation annotation = annotations.get(annotIndex);
            AnnotationSensitivity sensitivity = annotation.hasSensitivity(
                    MatchSensitivity.SENSITIVE) ?
                    annotation.sensitivity(MatchSensitivity.SENSITIVE) :
                    annotation.sensitivity(MatchSensitivity.INSENSITIVE);
            return forwardIndexReader.retrievePart(sensitivity.luceneField(), docId, start, end);
        }

        @Override
        public int getNumberOfAnnotations() {
            return ForwardIndexAccessorIntegrated.this.numberOfAnnotations();
        }

        @Override
        public String getTermString(int annotIndex, int termId) {
            // TODO: we don't need to translate to global term ids; get the term string at
            //   the segment level instead.
            int globalTermId = terms.get(annotIndex)
                    .segmentIdsToGlobalIds(readerContext, List.of(new int[] {termId})).get(0)[0];
            return ForwardIndexAccessorIntegrated.this.getTermString(annotIndex, globalTermId);
        }

        @Override
        public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
            // TODO: we don't need to translate to global term ids; the comparison could be done
            //   at the segment level instead.
            int[] globalTermIds = terms.get(annotIndex)
                    .segmentIdsToGlobalIds(readerContext, List.of(termId)).get(0);
            return ForwardIndexAccessorIntegrated.this.termsEqual(annotIndex, globalTermIds, sensitivity);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ForwardIndexAccessorIntegrated))
            return false;
        ForwardIndexAccessorIntegrated that = (ForwardIndexAccessorIntegrated) o;
        return index.equals(that.index) && annotatedField.equals(that.annotatedField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, annotatedField);
    }
}
