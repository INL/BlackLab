package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;

/**
 * Returns either the left edge or right edge of the specified query.
 *
 * Note that the results of this query are zero-length spans.
 */
public class TextPatternEdge extends TextPattern {

    private final TextPattern clause;

    private final boolean trailingEdge;

    public TextPatternEdge(TextPattern clause, boolean trailingEdge) {
        this.clause = clause;
        this.trailingEdge = trailingEdge;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryEdge(clause.translate(context), trailingEdge);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternEdge that = (TextPatternEdge) o;
        return trailingEdge == that.trailingEdge && Objects.equals(clause, that.clause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, trailingEdge);
    }

    @Override
    public String toString() {
        return "EDGE(" + clause + ", " + (trailingEdge ? "R" : "L") + ")";
    }

    public TextPattern getClause() {
        return clause;
    }

    public boolean isTrailingEdge() {
        return trailingEdge;
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
