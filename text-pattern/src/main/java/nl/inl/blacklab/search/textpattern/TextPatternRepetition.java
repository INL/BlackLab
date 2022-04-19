package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryRepetition;

/**
 * Repetition of a pattern.
 */
public class TextPatternRepetition extends TextPattern {
    private final TextPattern base;

    private final int min;

    private final int max;

    public TextPatternRepetition(TextPattern base, int min, int max) {
        this.base = base;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0 || this.max < 0)
            throw new IllegalArgumentException("min or max can't be negative");
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        BLSpanQuery baseTranslated = base.translate(context);

        if (min == 1 && max == 1)
            return baseTranslated; // no repetition

        return new SpanQueryRepetition(baseTranslated, min, max);
    }

    public TextPattern getClause() {
        return base;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternRepetition) {
            TextPatternRepetition tp = ((TextPatternRepetition) obj);
            return base.equals(tp.base) && min == tp.min && max == tp.max;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return base.hashCode() + 13 * min + 31 * max;
    }

    @Override
    public String toString() {
        return "REP(" + base.toString() + ", " + min + ", " + inf(max) + ")";
    }

}
