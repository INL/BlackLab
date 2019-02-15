package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFilterNGrams;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter.Operation;

public class TextPatternFilterNGrams extends TextPattern {

    protected TextPattern clause;

    protected Operation op;

    /*
     * The minimum hit length
     */
    protected int min;

    /*
     * The maximum hit length
     */
    protected int max;

    public TextPatternFilterNGrams(TextPattern clause, Operation op, int min, int max) {
        this.clause = clause;
        this.op = op;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min or max can't be negative");
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryFilterNGrams(clause.translate(context), op, min, max, 0, 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternFilterNGrams) {
            TextPatternFilterNGrams tp = ((TextPatternFilterNGrams) obj);
            return clause.equals(tp.clause) && op == tp.op && min == tp.min && max == tp.max;
        }
        return false;
    }

    public Operation getOperation() {
        return op;
    }

    public TextPattern getClause() {
        return clause;
    }

    @Override
    public int hashCode() {
        return clause.hashCode() + op.hashCode() + 13 * min + 31 * max;
    }

    @Override
    public String toString() {
        return "FILTERNGRAMS(" + clause + ", " + op + ", " + min + ", " + inf(max) + ")";
    }

}
