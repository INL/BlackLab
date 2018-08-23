package nl.inl.blacklab.search.matchfilter;

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;

public class MatchFilterTokenPropertyEqualsString extends MatchFilter {
    private String groupName;

    private int groupIndex;

    private String propertyName;

    private int propIndex = -1;

    private String compareToTermString;

    private int compareToTermId = -1;

    private MutableIntSet compareToTermIds;

    private MatchSensitivity sensitivity;

    public MatchFilterTokenPropertyEqualsString(String label, String propertyName, String termString,
            MatchSensitivity sensitivity) {
        this.groupName = label;
        this.propertyName = propertyName;
        this.compareToTermString = termString;
        this.sensitivity = sensitivity;
    }

    @Override
    public String toString() {
        return groupName + (propertyName == null ? "" : "." + propertyName) + " = " + compareToTermString;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
        result = prime * result + ((propertyName == null) ? 0 : propertyName.hashCode());
        result = prime * result + ((compareToTermString == null) ? 0 : compareToTermString.hashCode());
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
        MatchFilterTokenPropertyEqualsString other = (MatchFilterTokenPropertyEqualsString) obj;
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
        if (compareToTermString == null) {
            if (other.compareToTermString != null)
                return false;
        } else if (!compareToTermString.equals(other.compareToTermString))
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
        int leftTermId = fiDoc.getToken(propIndex, tokenPosition);
        if (compareToTermId >= 0)
            return ConstraintValue.get(leftTermId == compareToTermId); // just a single term to compare to
        return ConstraintValue.get(compareToTermIds.contains(leftTermId)); // multiple terms, use set.contains()
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        if (propertyName != null) {
            propIndex = fiAccessor.getAnnotationNumber(propertyName);
            compareToTermIds = new IntHashSet();
            compareToTermId = -1;
            fiAccessor.getTermNumbers(compareToTermIds, propIndex, compareToTermString, sensitivity);
            if (compareToTermIds.size() == 1) {
                compareToTermId = compareToTermIds.intIterator().next();
            }
        }
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

}
