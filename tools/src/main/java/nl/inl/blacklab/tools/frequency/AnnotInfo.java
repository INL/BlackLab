package nl.inl.blacklab.tools.frequency;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Info about an annotation we're grouping on.
 */
final class AnnotInfo {
    private final AnnotationForwardIndex annotationForwardIndex;

    private final MatchSensitivity matchSensitivity;

    private final Terms terms;

    public AnnotationForwardIndex getAnnotationForwardIndex() {
        return annotationForwardIndex;
    }

    public MatchSensitivity getMatchSensitivity() {
        return matchSensitivity;
    }

    public Terms getTerms() {
        return terms;
    }

    public AnnotInfo(AnnotationForwardIndex annotationForwardIndex, MatchSensitivity matchSensitivity) {
        this.annotationForwardIndex = annotationForwardIndex;
        this.matchSensitivity = matchSensitivity;
        this.terms = annotationForwardIndex.terms();
    }
}
