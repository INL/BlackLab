package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;

public class MatchFilterString extends MatchFilter {
    ConstraintValueString string;

    /** -1 if we don't know the annotation index, or the annotation index otherwise */
    int annotIndex = -1;

    /**
     * Term index if we know the annotation index to use for this expression (i.e.
     * propIndex >= 0), or -1 if not
     */
    int termIndex = -1;

    public MatchFilterString(String string) {
        this.string = new ConstraintValueString(string);
    }

    public String getString() {
        return string.getValue();
    }

    @Override
    public String toString() {
        return "\"" + string + "\"";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((string == null) ? 0 : string.hashCode());
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
        MatchFilterString other = (MatchFilterString) obj;
        if (string == null) {
            if (other.string != null)
                return false;
        } else if (!string.equals(other.string))
            return false;
        return true;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        // NOP
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, Span[] capturedGroups) {
        return string;
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        // NOP
    }

    public int getPropIndex() {
        return annotIndex;
    }

    public int getTermIndex() {
        return termIndex;
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }
}
