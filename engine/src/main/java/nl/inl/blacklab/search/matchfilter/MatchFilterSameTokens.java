package nl.inl.blacklab.search.matchfilter;

import java.util.Arrays;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;

public class MatchFilterSameTokens extends MatchFilter {
    private final String annotationName;

    private int annotIndex = -1;

    private final String[] groupName;

    private int[] groupIndex;

    private final MatchSensitivity sensitivity;

    public MatchFilterSameTokens(String leftGroup, String rightGroup, String annotationName, MatchSensitivity sensitivity) {
        this.groupName = new String[] { leftGroup, rightGroup };
        this.groupIndex = new int[2];

        this.annotationName = annotationName;
        this.sensitivity = sensitivity;
    }

    @Override
    public String toString() {
        String annotPart = annotationName == null ? "" : "." + annotationName;
        return groupName[0] + annotPart + " = " + groupName[1] + annotPart;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(groupIndex);
        result = prime * result + Arrays.hashCode(groupName);
        result = prime * result + annotIndex;
        result = prime * result + ((annotationName == null) ? 0 : annotationName.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
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
        MatchFilterSameTokens other = (MatchFilterSameTokens) obj;
        if (!Arrays.equals(groupIndex, other.groupIndex))
            return false;
        if (!Arrays.equals(groupName, other.groupName))
            return false;
        if (annotIndex != other.annotIndex)
            return false;
        if (annotationName == null) {
            if (other.annotationName != null)
                return false;
        } else if (!annotationName.equals(other.annotationName))
            return false;
        return sensitivity == other.sensitivity;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        groupIndex = new int[2];
        for (int i = 0; i < 2; i++)
            groupIndex[i] = context.registerCapturedGroup(groupName[i]);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, Span[] capturedGroups) {
        int[] termId = new int[2];
        for (int i = 0; i < 2; i++) {
            Span span = capturedGroups[groupIndex[i]];
            if (span == null)
                return ConstraintValue.get(false); // if either side is undefined, they are not equal
            int tokenPosition = span.start();
            if (annotIndex < 0)
                termId[i] = tokenPosition;
            else
                termId[i] = fiDoc.getToken(annotIndex, tokenPosition);
        }
        if (sensitivity == MatchSensitivity.SENSITIVE)
            return ConstraintValue.get(termId[0] == termId[1]);
        // (Somewhat) insensitive; let Terms determine if term ids have the same sort position or not
        return ConstraintValue.get(fiDoc.termsEqual(annotIndex, termId, sensitivity));
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        if (annotationName != null) {
            annotIndex = fiAccessor.getAnnotationNumber(annotationName);
        }
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

}
