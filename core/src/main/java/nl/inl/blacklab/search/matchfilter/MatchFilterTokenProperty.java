package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;

public class MatchFilterTokenProperty extends MatchFilter {
    private String groupName;

    private int groupIndex;

    private String propertyName;

    private int propIndex = -1;

    public MatchFilterTokenProperty(String label, String propertyName) {
        this.groupName = label;
        this.propertyName = propertyName;
    }

    @Override
    public String toString() {
        return groupName + (propertyName == null ? "" : "." + propertyName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
        result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
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
        MatchFilterTokenProperty other = (MatchFilterTokenProperty) obj;
        if (groupName == null) {
            if (other.groupName != null)
                return false;
        } else if (!groupName.equals(other.groupName))
            return false;
        if (propertyName == null) {
            if (other.propertyName != null)
                return false;
        } else if (!propertyName.equals(other.propertyName))
            return false;
        return true;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        groupIndex = context.registerCapturedGroup(groupName);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, Span[] capturedGroups) {
        Span span = capturedGroups[groupIndex];
        if (span == null)
            return ConstraintValue.undefined();
        int tokenPosition = span.start();
        if (propIndex < 0)
            return ConstraintValue.get(tokenPosition);
        int termId = fiDoc.getToken(propIndex, tokenPosition);
        String term = fiDoc.getTermString(propIndex, termId);
        return ConstraintValue.get(term);
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        if (propertyName != null)
            propIndex = fiAccessor.getAnnotationNumber(propertyName);
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public MatchFilter matchTokenString(String str, MatchSensitivity sensitivity) {
        return new MatchFilterTokenPropertyEqualsString(groupName, propertyName, str, sensitivity);
    }

    public MatchFilter matchOtherTokenSameProperty(String otherGroupName, MatchSensitivity sensitivity) {
        return new MatchFilterSameTokens(groupName, otherGroupName, propertyName, sensitivity);
    }

    public boolean hasProperty() {
        return propertyName != null;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getGroupName() {
        return groupName;
    }

}
