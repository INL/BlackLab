package nl.inl.blacklab.search.fimatch;

import org.apache.lucene.index.LeafReaderContext;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 *
 * Thread-safe.
 */
public class ForwardIndexAccessorExternal extends ForwardIndexAccessorAbstract {

    public ForwardIndexAccessorExternal(BlackLabIndex index, AnnotatedField searchField) {
        super(index, searchField);
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

        /**
         * Get a token source, which we can use to get tokens from a document for
         * different annotations.
         *
         * @param id Lucene document id
         * @return the token source
         */
        @Override
        public ForwardIndexDocument advanceForwardIndexDoc(int id) {
            return new ForwardIndexDocumentImpl(this, id);
        }

        @Override
        public int getDocLength(int docId) {
            // NOTE: we subtract one because we always have an "extra closing token" at the end that doesn't
            //       represent a word, just any closing punctuation after the last word.
            return fis.get(0).docLength(docId) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }

        final int[] starts = { 0 };
        final int[] ends = { 0 };

        @Override
        public int[] getChunk(int annotIndex, int docId, int start, int end) {
            starts[0] = start;
            ends[0] = end;
            return fis.get(annotIndex).retrievePartsInt(docId, starts, ends).get(0);
        }

        @Override
        public int getNumberOfAnnotations() {
            return ForwardIndexAccessorExternal.this.numberOfAnnotations();
        }

        @Override
        public String getTermString(int annotIndex, int termId) {
            return ForwardIndexAccessorExternal.this.getTermString(annotIndex, termId);
        }

        @Override
        public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
            return ForwardIndexAccessorExternal.this.termsEqual(annotIndex, termId, sensitivity);
        }
    }
}
