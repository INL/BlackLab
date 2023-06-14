package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

/** Compare two constraint values and return an integer comparison result. */
public class MatchFilterCompare extends MatchFilter {

    public enum Operator {
        EQUAL("="),
        NOT_EQUAL("!="),
        LESS_THAN("<"),
        GREATER_THAN(">"),
        LESS_OR_EQUAL("<="),
        GREATER_OR_EQUAL(">=");

        public static Operator fromSymbol(String s) {
            for (Operator op: values()) {
                if (op.symbol.equals(s))
                    return op;
            }
            throw new IllegalArgumentException("Unknown operator: " + s);
        }

        private String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String toString() {
            return symbol;
        }

        public boolean perform(int cmp) {
            switch (this) {
            case EQUAL: return cmp == 0;
            case NOT_EQUAL: return cmp != 0;
            case LESS_THAN: return cmp < 0;
            case GREATER_THAN: return cmp > 0;
            case LESS_OR_EQUAL: return cmp <= 0;
            case GREATER_OR_EQUAL: return cmp >= 0;
            }
            throw new IllegalArgumentException("Unknown operator: " + this);
        }
    }

    private final MatchFilter a;
    private final MatchFilter b;
    private final Operator op;
    private final MatchSensitivity sensitivity;

    public MatchFilterCompare(MatchFilter a, MatchFilter b, Operator op, MatchSensitivity sensitivity) {
        super();
        this.a = a;
        this.b = b;
        this.op = op;
        this.sensitivity = sensitivity;
    }

    @Override
    public String toString() {
        return a + " " + op + " " + b;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((a == null) ? 0 : a.hashCode());
        result = prime * result + ((b == null) ? 0 : b.hashCode());
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
        MatchFilterCompare other = (MatchFilterCompare) obj;
        if (a == null) {
            if (other.a != null)
                return false;
        } else if (!a.equals(other.a))
            return false;
        if (b == null) {
            if (other.b != null)
                return false;
        } else if (!b.equals(other.b))
            return false;
        return sensitivity == other.sensitivity;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        a.setHitQueryContext(context);
        b.setHitQueryContext(context);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        ConstraintValue ra = a.evaluate(fiDoc, matchInfo);
        ConstraintValue rb = b.evaluate(fiDoc, matchInfo);

        // Compare values
        int cmp;
        if (ra instanceof ConstraintValueString && rb instanceof ConstraintValueString) {
            cmp = ((ConstraintValueString)ra).stringCompareTo((ConstraintValueString) rb, sensitivity);
        } else {
            cmp = ra.compareTo(rb);
        }
        // Return result of comparison depending on operator
        return ConstraintValue.get(op.perform(cmp));
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        a.lookupAnnotationIndices(fiAccessor);
        b.lookupAnnotationIndices(fiAccessor);
    }

    @Override
    public MatchFilter rewrite() {
        MatchFilter x = a.rewrite();
        MatchFilter y = b.rewrite();

        if (op == Operator.EQUAL) {
            if (x instanceof MatchFilterTokenAnnotation && ((MatchFilterTokenAnnotation) x).hasAnnotation()
                    && y instanceof MatchFilterString) {
                // Simple annotation to string comparison, e.g. a.word = "cow"
                // This can be done more efficiently without string comparisons
                String termString = ((MatchFilterString) y).getString();
                return ((MatchFilterTokenAnnotation) x).matchTokenString(termString, sensitivity);
            }

            if (x instanceof MatchFilterTokenAnnotation && y instanceof MatchFilterTokenAnnotation) {
                MatchFilterTokenAnnotation xtp = ((MatchFilterTokenAnnotation) x);
                MatchFilterTokenAnnotation ytp = ((MatchFilterTokenAnnotation) y);
                if (xtp.getAnnotationName().equals(ytp.getAnnotationName())) {
                    // Expression of the form a.word = b.word;
                    // This can be done more efficiently without string comparisons
                    return xtp.matchOtherTokenSameProperty(ytp.getGroupName(), sensitivity);
                }
            }
        }

        // Some other comparison.
        if (x != a || y != b) {
            // clauses rewritten; return new instance
            return new MatchFilterCompare(x, y, op, sensitivity);
        }
        // return unchanged
        return this;
    }

}
