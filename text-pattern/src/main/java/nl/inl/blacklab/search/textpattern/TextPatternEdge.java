package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;

/**
 * Returns either the left edge or right edge of the specified query.
 *
 * Note that the results of this query are zero-length spans.
 */
public class TextPatternEdge extends TextPatternCombiner {

    private final boolean rightEdge;

    public TextPatternEdge(TextPattern clause, boolean rightEdge) {
        super(clause);
        this.rightEdge = rightEdge;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryEdge(clauses.get(0).translate(context), rightEdge);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternEdge) {
            return super.equals(obj) && ((TextPatternEdge) obj).rightEdge == rightEdge;
        }
        return false;
    }

    public boolean isRightEdge() {
        return rightEdge;
    }

    public TextPattern getClause() {
        return clauses.get(0);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + (rightEdge ? 13 : 0);
    }

    @Override
    public String toString() {
        return "EDGE(" + clauses.get(0).toString() + ", " + (rightEdge ? "R" : "L") + ")";
    }

}
