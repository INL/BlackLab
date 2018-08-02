package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.LeafReader;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.lucene.DocIntFieldGetter;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any property at any position from a
 * document.
 */
class ForwardIndexAccessorImpl extends ForwardIndexAccessor {

    /** Our Searcher object */
    private Searcher searcher;

    /** Field name, e.g. "contents" */
    String complexFieldBaseName;

    /** The property index for each property name */
    private Map<String, Integer> propertyNumbers = new HashMap<>();

    /** The property names for each property */
    List<String> propertyNames = new ArrayList<>();

    /** The forward index for each property */
    List<ForwardIndex> fis = new ArrayList<>();

    /** The terms object for each property */
    private List<Terms> terms = new ArrayList<>();

    ForwardIndexAccessorImpl(Searcher searcher, String searchField) {
        this.searcher = searcher;
        this.complexFieldBaseName = searchField;
    }

    /**
     * Get the index number corresponding to the given property name.
     *
     * @param propertyName property to get the index for
     * @return index for this property
     */
    @Override
    public int getPropertyNumber(String propertyName) {
        Integer n = propertyNumbers.get(propertyName);
        if (n == null) {
            // Assign number and store reference to forward index
            n = propertyNumbers.size();
            propertyNumbers.put(propertyName, n);
            propertyNames.add(propertyName);
            ForwardIndex fi = searcher
                    .getForwardIndex(ComplexFieldUtil.propertyField(complexFieldBaseName, propertyName));
            fis.add(fi);
            terms.add(fi.getTerms());
        }
        return n;
    }

    @Override
    public void getTermNumbers(MutableIntSet results, int propertyNumber, String propertyValue, boolean caseSensitive,
            boolean diacSensitive) {
        terms.get(propertyNumber).indexOf(results, propertyValue, caseSensitive, diacSensitive);
    }

    public int getTermAtPosition(int fiid, int propertyNumber, int pos) {
        return fis.get(propertyNumber).getToken(fiid, pos);
    }

    @Override
    public String getTermString(int propIndex, int termId) {
        return fis.get(propIndex).getTerms().get(termId);
    }

    @Override
    public boolean termsEqual(int propIndex, int[] termId, boolean caseSensitive, boolean diacSensitive) {
        return fis.get(propIndex).getTerms().termsEqual(termId, caseSensitive, diacSensitive);
    }

    @Override
    public int numberOfProperties() {
        return fis.size();
    }

    @Override
    public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReader reader) {
        return new ForwardIndexAccessorLeafReaderImpl(reader);
    }

    class ForwardIndexAccessorLeafReaderImpl extends ForwardIndexAccessorLeafReader {

        private List<DocIntFieldGetter> fiidGetters;

        ForwardIndexAccessorLeafReaderImpl(LeafReader reader) {
            super(reader);
            fiidGetters = new ArrayList<>();
            for (int i = 0; i < getNumberOfProperties(); i++)
                fiidGetters.add(null);
        }

        DocIntFieldGetter fiidGetter(int propIndex) {
            DocIntFieldGetter g = fiidGetters.get(propIndex);
            if (g == null) {
                String propertyName = propertyNames.get(propIndex);
                String propFieldName = ComplexFieldUtil.propertyField(complexFieldBaseName, propertyName);
                String fiidFieldName = ComplexFieldUtil.forwardIndexIdField(propFieldName);
                g = new DocIntFieldGetter(reader, fiidFieldName);
                fiidGetters.set(propIndex, g);
            }
            return g;
        }

        /**
         * Get a token source, which we can use to get tokens from a document for
         * different properties.
         *
         * @param id Lucene document id
         * @return the token source
         */
        @Override
        public ForwardIndexDocument getForwardIndexDoc(int id) {
            return new ForwardIndexDocumentImpl(this, id);
        }

        @Override
        public int getDocLength(int docId) {
            // NOTE: we subtract one because we always have a closing token at the end that doesn't
            //       represent a word, just any closing punctuation after the last word.
            return fis.get(0).getDocLength(getFiid(0, docId)) - 1;
        }

        int[] starts = { 0 }, ends = { 0 };

        @Override
        public int[] getChunk(int propIndex, int docId, int start, int end) {
            starts[0] = start;
            ends[0] = end;
            int fiid = fiidGetter(propIndex).getFieldValue(docId);
            return fis.get(propIndex).retrievePartsInt(fiid, starts, ends).get(0);
        }

        @Override
        public int getFiid(int propIndex, int docId) {
            return fiidGetter(propIndex).getFieldValue(docId);
        }

        @Override
        public int getNumberOfProperties() {
            return ForwardIndexAccessorImpl.this.numberOfProperties();
        }

    }

}
