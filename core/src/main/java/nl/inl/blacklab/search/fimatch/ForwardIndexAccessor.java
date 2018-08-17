package nl.inl.blacklab.search.fimatch;

import org.apache.lucene.index.LeafReader;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 */
public abstract class ForwardIndexAccessor {

    public static ForwardIndexAccessor fromIndex(BlackLabIndex index, String searchField) {
        return new ForwardIndexAccessorImpl(index, index.annotatedField(searchField));
    }
    
    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotation annotation to get the index for
     * @return index for this annotation
     */
    public abstract int getAnnotationNumber(Annotation annotation);

    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotationName annotation to get the index for
     * @return index for this annotation
     */
    public abstract int getAnnotationNumber(String annotationName);
    
    /**
     * Get the term number for a given term string.
     *
     * @param results (out) term number for this term in this annotation
     * @param annotationNumber which annotation to get term number for
     * @param annotationValue which term string to get term number for
     * @param sensitivity match sensitively or not? (currently both or neither)
     *
     */
    public abstract void getTermNumbers(MutableIntSet results, int annotationNumber, String annotationValue,
            MatchSensitivity sensitivity);

    /**
     * Get the number of annotations
     * 
     * @return number of annotations
     */
    public abstract int numberOfAnnotations();

    /**
     * Get an accessor for forward index documents from this leafreader.
     *
     * @param reader index reader
     * @return reader-specific accessor
     */
    public abstract ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReader reader);

    public abstract class ForwardIndexAccessorLeafReader {

        protected LeafReader reader;

        ForwardIndexAccessorLeafReader(LeafReader reader) {
            this.reader = reader;
        }

        /**
         * Get a token source, which we can use to get tokens from a document for
         * different annotations.
         *
         * @param docId Lucene document id
         * @return the token source
         */
        public abstract ForwardIndexDocument getForwardIndexDoc(int docId);

        /**
         * Return the document length in tokens
         * 
         * @param docId Lucene document id
         * @return document length in tokens
         */
        public abstract int getDocLength(int docId);

        /**
         * Get a chunk of tokens from a forward index
         *
         * @param annotIndex annotation to get tokens for
         * @param docId Lucene document id
         * @param start first token to get
         * @param end one more than the last token to get
         * @return document length in tokens
         */
        abstract int[] getChunk(int annotIndex, int docId, int start, int end);

        /**
         * Get the forward index id for the specified annotation and document.
         *
         * @param annotIndex annotation to get tokens for
         * @param docId Lucene document id
         * @return document length in tokens
         */
        abstract int getFiid(int annotIndex, int docId);

        /**
         * Get the number of mapped annotations.
         *
         * Annotations are mapped before the matching starts, so we can simply pass an
         * annotation index instead of annotation names, which would be too slow.
         *
         * @return number of mapped annotations.
         */
        public int getNumberOfAnnotations() {
            return ForwardIndexAccessor.this.numberOfAnnotations();
        }

        public String getTermString(int annotIndex, int termId) {
            return ForwardIndexAccessor.this.getTermString(annotIndex, termId);
        }

        public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
            return ForwardIndexAccessor.this.termsEqual(annotIndex, termId, sensitivity);
        }

    }

    public abstract String getTermString(int annotIndex, int termId);

    public abstract boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity);
}
