package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;

/**
 * A TextPattern searching for TextPatterns that contain a hit from another
 * TextPattern. This may be used to search for sentences containing a certain
 * word, etc.
 */
public class TextPatternPositionFilter extends TextPatternCombiner {

    /** The hits we're (possibly) looking for */
    SpanQueryPositionFilter.Operation op;

    /** Whether to invert the filter operation */
    boolean invert;

    /** How to adjust the left edge of the producer hits while matching */
    int leftAdjust = 0;

    /** How to adjust the right edge of the producer hits while matching */
    int rightAdjust = 0;

    public TextPatternPositionFilter(TextPattern producer, TextPattern filter, SpanQueryPositionFilter.Operation op) {
        this(producer, filter, op, false);
    }

    public TextPatternPositionFilter(TextPattern producer, TextPattern filter, SpanQueryPositionFilter.Operation op,
            boolean invert) {
        super(producer, filter);
        this.op = op;
        this.invert = invert;
    }

    /**
     * Adjust the left edge of the producer hits for matching only.
     *
     * That is, the original producer hit is returned, not the adjusted one.
     *
     * @param delta how to adjust the edge
     */
    public void adjustLeft(int delta) {
        leftAdjust += delta;
    }

    /**
     * Adjust the right edge of the producer hits for matching only.
     *
     * That is, the original producer hit is returned, not the adjusted one.
     *
     * @param delta how to adjust the edge
     */
    public void adjustRight(int delta) {
        rightAdjust += delta;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery trContainers = clauses.get(0).translate(context);
        BLSpanQuery trSearch = clauses.get(1).translate(context);
        return new SpanQueryPositionFilter(trContainers, trSearch, op, invert, leftAdjust, rightAdjust);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternPositionFilter) {
            return super.equals(obj) && ((TextPatternPositionFilter) obj).invert == invert;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() + op.hashCode() + (invert ? 13 : 0) + leftAdjust * 31 + rightAdjust * 37;
    }

    @Override
    public String toString() {
        String producer = clauses.get(0).toString();
        String filter = clauses.get(1).toString();
        String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
        return "POSFILTER(" + producer + ", " + filter + ", " + (invert ? "NOT" : "") + op + adj + ")";
    }
}
