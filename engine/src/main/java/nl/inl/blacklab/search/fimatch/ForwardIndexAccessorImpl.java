package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.LeafReader;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.DocIntFieldGetter;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 */
class ForwardIndexAccessorImpl extends ForwardIndexAccessor {

    /** Our index */
    private BlackLabIndex index;

    /** Field name, e.g. "contents" */
    AnnotatedField annotatedField;

    /** The annotation index for each annotation name */
    private Map<Annotation, Integer> annotationNumbers = new HashMap<>();

    /** The annotation names for each annotation */
    List<Annotation> annotationNames = new ArrayList<>();

    /** The forward index for each annotation */
    List<AnnotationForwardIndex> fis = new ArrayList<>();

    /** The terms object for each annotation */
    private List<Terms> terms = new ArrayList<>();

    ForwardIndexAccessorImpl(BlackLabIndex index, AnnotatedField searchField) {
        this.index = index;
        this.annotatedField = searchField;
    }

    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotation annotation to get the index for
     * @return index for this annotation
     */
    @Override
    public int getAnnotationNumber(Annotation annotation) {
        Integer n = annotationNumbers.get(annotation);
        if (n == null) {
            // Assign number and store reference to forward index
            n = annotationNumbers.size();
            annotationNumbers.put(annotation, n);
            annotationNames.add(annotation);
            AnnotationForwardIndex fi = index.annotationForwardIndex(annotation);
            fis.add(fi);
            terms.add(fi.terms());
        }
        return n;
    }

    @Override
    public int getAnnotationNumber(String annotationName) {
        return getAnnotationNumber(annotatedField.annotation(annotationName));
    }

    @Override
    public void getTermNumbers(MutableIntSet results, int annotationNumber, String annotationValue, MatchSensitivity sensitivity) {
        terms.get(annotationNumber).indexOf(results, annotationValue, sensitivity);
    }

    public int getTermAtPosition(int fiid, int annotationNumber, int pos) {
        return fis.get(annotationNumber).getToken(fiid, pos);
    }

    @Override
    public String getTermString(int annotIndex, int termId) {
        return fis.get(annotIndex).terms().get(termId);
    }

    @Override
    public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
        return fis.get(annotIndex).terms().termsEqual(termId, sensitivity);
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
                Annotation annotation = annotationNames.get(annotIndex);
                g = new DocIntFieldGetter(reader, annotation.forwardIndexIdField());
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
            // NOTE: we subtract one because we always have an "extra closing token" at the end that doesn't
            //       represent a word, just any closing punctuation after the last word.
            return fis.get(0).docLength(getFiid(0, docId)) - 1;
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((annotatedField == null) ? 0 : annotatedField.hashCode());
        result = prime * result + ((annotationNames == null) ? 0 : annotationNames.hashCode());
        result = prime * result + ((annotationNumbers == null) ? 0 : annotationNumbers.hashCode());
        result = prime * result + ((fis == null) ? 0 : fis.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
        result = prime * result + ((terms == null) ? 0 : terms.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ForwardIndexAccessorImpl other = (ForwardIndexAccessorImpl) obj;
        if (annotatedField == null) {
            if (other.annotatedField != null)
                return false;
        } else if (!annotatedField.name().equals(other.annotatedField.name()))
            return false;
        if (annotationNames == null) {
            if (other.annotationNames != null)
                return false;
        } else if (!annotationNames.equals(other.annotationNames))
            return false;
        if (annotationNumbers == null) {
            if (other.annotationNumbers != null)
                return false;
        } else if (!annotationNumbers.equals(other.annotationNumbers))
            return false;
        if (fis == null) {
            if (other.fis != null)
                return false;
        } else if (!fis.equals(other.fis))
            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        if (terms == null) {
            if (other.terms != null)
                return false;
        } else if (!terms.equals(other.terms))
            return false;
        return true;
    }



}
