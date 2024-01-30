package nl.inl.blacklab.search.lucene;

import java.util.Objects;

/**
 * Position information about a relation's source and target
 */
public class SpanInfo implements MatchInfo {

    int start;

    int end;

    public SpanInfo(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getSpanStart() {
        return start;
    }

    public int getSpanEnd() {
        return end;
    }

    @Override
    public Type getType() {
        return Type.SPAN;
    }

    @Override
    public String toString() {
        return "span(" + getSpanStart() + "-" + getSpanEnd() + ")";
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof SpanInfo)
            return compareTo((SpanInfo) o);
        return MatchInfo.super.compareTo(o);
    }

    public int compareTo(SpanInfo o) {
        int n;
        n = Integer.compare(start, o.start);
        if (n != 0)
            return n;
        n = Integer.compare(end, o.end);
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanInfo spanInfo = (SpanInfo) o;
        return start == spanInfo.start && end == spanInfo.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
