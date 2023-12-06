package nl.inl.blacklab.search;

/**
 * A start and end position (no document id).
 * 
 * Used for captured groups.
 */
public class Span {
    private final int start;
    private final int end;

    public Span(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public static Span singleWord(int wordNumber) {
        return new Span(wordNumber, wordNumber + 1);
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public int length() {
        return end - start;
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }
}
