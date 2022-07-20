package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public abstract class ForwardIndexAccessorAbstract implements ForwardIndexAccessor {
    /** Our index */
    protected final BlackLabIndex index;
    /** Field name, e.g. "contents" */
    final AnnotatedField annotatedField;
    /** The annotation names for each annotation */
    final List<Annotation> annotations = new ArrayList<>();
    /** The forward index for each annotation */
    final List<AnnotationForwardIndex> fis = new ArrayList<>();
    /** The annotation index for each annotation name */
    private final Map<Annotation, Integer> annotationNumbers = new HashMap<>();
    /** The terms object for each annotation */
    private final List<Terms> terms = new ArrayList<>();

    public ForwardIndexAccessorAbstract(BlackLabIndex index, AnnotatedField searchField) {
        this.index = index;
        this.annotatedField = searchField;
    }

    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotation annotation to get the index for
     * @return index for this annotation
     */
    public int getAnnotationNumber(Annotation annotation) {
        Integer n = annotationNumbers.get(annotation);
        if (n == null) {
            // Assign number and store reference to forward index
            n = annotationNumbers.size();
            annotationNumbers.put(annotation, n);
            annotations.add(annotation);
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
    public void getTermNumbers(MutableIntSet results, int annotationNumber, String annotationValue,
            MatchSensitivity sensitivity) {
        terms.get(annotationNumber).indexOf(results, annotationValue, sensitivity);
    }

    public String getTermString(int annotIndex, int termId) {
        return fis.get(annotIndex).terms().get(termId);
    }

    public boolean termsEqual(int annotIndex, int[] termId, MatchSensitivity sensitivity) {
        return fis.get(annotIndex).terms().termsEqual(termId, sensitivity);
    }

    public int numberOfAnnotations() {
        return fis.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ForwardIndexAccessorExternal))
            return false;
        ForwardIndexAccessorExternal that = (ForwardIndexAccessorExternal) o;
        return index.equals(that.index) && annotatedField.equals(that.annotatedField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, annotatedField);
    }
}
