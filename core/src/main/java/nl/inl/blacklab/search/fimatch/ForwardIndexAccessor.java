package nl.inl.blacklab.search.fimatch;

import org.apache.lucene.index.LeafReader;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.search.Searcher;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any property at any position from a
 * document.
 */
public abstract class ForwardIndexAccessor {

    public static ForwardIndexAccessor fromSearcher(Searcher searcher, String searchField) {
        return new ForwardIndexAccessorImpl(searcher, searchField);
    }
    /**
     * Get the index number corresponding to the given property name.
     *
     * @param propertyName property to get the index for
     * @return index for this property
     */
    public abstract int getPropertyNumber(String propertyName);

    /**
     * Get the term number for a given term string.
     *
     * @param results (out) term number for this term in this property
     * @param propertyNumber which property to get term number for
     * @param propertyValue which term string to get term number for
     * @param caseSensitive match case sensitively or not?
     * @param diacSensitive match case sensitively or not? (currently ignored)
     *
     */
    public abstract void getTermNumbers(MutableIntSet results, int propertyNumber, String propertyValue,
            boolean caseSensitive, boolean diacSensitive);

    /**
     * Get the number of properties
     * 
     * @return number of properties
     */
    public abstract int numberOfProperties();

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
         * different properties.
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
         * @param propIndex property to get tokens for
         * @param docId Lucene document id
         * @param start first token to get
         * @param end one more than the last token to get
         * @return document length in tokens
         */
        abstract int[] getChunk(int propIndex, int docId, int start, int end);

        /**
         * Get the forward index id for the specified property and document.
         *
         * @param propIndex property to get tokens for
         * @param docId Lucene document id
         * @return document length in tokens
         */
        abstract int getFiid(int propIndex, int docId);

        /**
         * Get the number of mapped properties.
         *
         * Properties are mapped before the matching starts, so we can simply pass a
         * property index instead of property names, which would be too slow.
         *
         * @return number of mapped properties.
         */
        public int getNumberOfProperties() {
            return ForwardIndexAccessor.this.numberOfProperties();
        }

        public String getTermString(int propIndex, int termId) {
            return ForwardIndexAccessor.this.getTermString(propIndex, termId);
        }

        public boolean termsEqual(int propIndex, int[] termId, boolean caseSensitive, boolean diacSensitive) {
            return ForwardIndexAccessor.this.termsEqual(propIndex, termId, caseSensitive, diacSensitive);
        }

    }

    public abstract String getTermString(int propIndex, int termId);

    public abstract boolean termsEqual(int propIndex, int[] termId, boolean caseSensitive, boolean diacSensitive);

}
