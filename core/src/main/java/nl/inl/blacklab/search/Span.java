package nl.inl.blacklab.search;

/**
 * A start and end position (no document id).
 * 
 * Used for captured groups.
 */
public class Span {
    private int start, end;

    public Span(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }

}
