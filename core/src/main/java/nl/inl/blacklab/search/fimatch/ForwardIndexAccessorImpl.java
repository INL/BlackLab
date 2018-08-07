package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.LeafReader;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.lucene.DocIntFieldGetter;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 */
class ForwardIndexAccessorImpl extends ForwardIndexAccessor {

    /** Our Searcher object */
    private BlackLabIndex searcher;

    /** Field name, e.g. "contents" */
    String annotatedFieldBaseName;

    /** The annotation index for each annotation name */
    private Map<String, Integer> annotationNumbers = new HashMap<>();

    /** The annotation names for each annotation */
    List<String> annotationNames = new ArrayList<>();

    /** The forward index for each annotation */
    List<ForwardIndex> fis = new ArrayList<>();

    /** The terms object for each annotation */
    private List<Terms> terms = new ArrayList<>();

    ForwardIndexAccessorImpl(BlackLabIndex searcher, String searchField) {
        this.searcher = searcher;
        this.annotatedFieldBaseName = searchField;
    }

    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotationName annotation to get the index for
     * @return index for this annotation
     */
    @Override
    public int getAnnotationNumber(String annotationName) {
        Integer n = annotationNumbers.get(annotationName);
        if (n == null) {
            // Assign number and store reference to forward index
            n = annotationNumbers.size();
            annotationNumbers.put(annotationName, n);
            annotationNames.add(annotationName);
            ForwardIndex fi = searcher.forwardIndex(searcher.annotatedField(annotatedFieldBaseName).annotations().get(annotationName));
            fis.add(fi);
            terms.add(fi.getTerms());
        }
        return n;
    }

    @Override
    public void getTermNumbers(MutableIntSet results, int annotationNumber, String annotationValue, boolean caseSensitive,
            boolean diacSensitive) {
        terms.get(annotationNumber).indexOf(results, annotationValue, caseSensitive, diacSensitive);
    }

    public int getTermAtPosition(int fiid, int annotationNumber, int pos) {
        return fis.get(annotationNumber).getToken(fiid, pos);
    }

    @Override
    public String getTermString(int annotIndex, int termId) {
        return fis.get(annotIndex).getTerms().get(termId);
    }

    @Override
    public boolean termsEqual(int annotIndex, int[] termId, boolean caseSensitive, boolean diacSensitive) {
        return fis.get(annotIndex).getTerms().termsEqual(termId, caseSensitive, diacSensitive);
    }

    @Override
    public int numberOfAnnotations() {
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
            for (int i = 0; i < getNumberOfAnnotations(); i++)
                fiidGetters.add(null);
        }

        DocIntFieldGetter fiidGetter(int annotIndex) {
            DocIntFieldGetter g = fiidGetters.get(annotIndex);
            if (g == null) {
                String annotationName = annotationNames.get(annotIndex);
                String annotFieldName = AnnotatedFieldNameUtil.annotationField(annotatedFieldBaseName, annotationName);
                String fiidFieldName = AnnotatedFieldNameUtil.forwardIndexIdField(annotFieldName);
                g = new DocIntFieldGetter(reader, fiidFieldName);
                fiidGetters.set(annotIndex, g);
            }
            return g;
        }

        /**
         * Get a token source, which we can use to get tokens from a document for
         * different annotations.
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

        int[] starts = { 0 };
        int[] ends = { 0 };

        @Override
        public int[] getChunk(int annotIndex, int docId, int start, int end) {
            starts[0] = start;
            ends[0] = end;
            int fiid = fiidGetter(annotIndex).getFieldValue(docId);
            return fis.get(annotIndex).retrievePartsInt(fiid, starts, ends).get(0);
        }

        @Override
        public int getFiid(int annotIndex, int docId) {
            return fiidGetter(annotIndex).getFieldValue(docId);
        }

        @Override
        public int getNumberOfAnnotations() {
            return ForwardIndexAccessorImpl.this.numberOfAnnotations();
        }

    }

}
