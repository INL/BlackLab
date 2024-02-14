package nl.inl.blacklab.search.lucene;

import java.util.Objects;

/**
 * Position information about a relation's source and target
 */
public class SpanInfo extends MatchInfo {

    public static SpanInfo create(int start, int end, String overriddenField) {
        return new SpanInfo(start, end, overriddenField);
    }

    int start;

    int end;

    private SpanInfo(int start, int end, String overriddenField) {
        super(overriddenField);
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
    public String toString(String defaultField) {
        return "span(" + getSpanStart() + "-" + getSpanEnd() + ")" + toStringOptFieldName(defaultField);
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof SpanInfo)
            return compareTo((SpanInfo) o);
        return super.compareTo(o);
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
