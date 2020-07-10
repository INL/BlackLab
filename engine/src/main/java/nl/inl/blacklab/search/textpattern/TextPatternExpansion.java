package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;

public class TextPatternExpansion extends TextPattern {

    protected TextPattern clause;

    protected Direction direction;

    /*
     * The minimum number of tokens in this stretch.
     */
    protected int min;

    /*
     * The maximum number of tokens in this stretch.
     */
    protected int max;

    public TextPatternExpansion(TextPattern clause, Direction direction, int min, int max) {
        this.clause = clause;
        this.direction = direction;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min or max can't be negative");
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryExpansion(clause.translate(context), direction, min, max);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternExpansion) {
            TextPatternExpansion tp = ((TextPatternExpansion) obj);
            return clause.equals(tp.clause) && direction == tp.direction && min == tp.min && max == tp.max;
        }
        return false;
    }

    public int getMinExpand() {
        return min;
    }

    public int getMaxExpand() {
        return max;
    }

    public boolean isExpandToLeft() {
        return direction == Direction.LEFT;
    }

    public TextPattern getClause() {
        return clause;
    }

    @Override
    public int hashCode() {
        return clause.hashCode() + 1023 * direction.hashCode() + 13 * min + 31 * max;
    }

    @Override
    public String toString() {
        return "EXPAND(" + clause + ", " + direction + ", " + min + ", " + inf(max) + ")";
    }

}
