package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.lucene.SpanQueryFixedSpan;

/**
 * A TextPattern matching a word.
 */
public class TextPatternFixedSpan extends TextPattern {
    protected final int start;

    protected final int end;

    public TextPatternFixedSpan(int start, int end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryFixedSpan(QueryInfo.create(context.index()), context.luceneField(), start, end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternFixedSpan that = (TextPatternFixedSpan) o;
        return start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "TextPatternFixedSpan{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }
}
