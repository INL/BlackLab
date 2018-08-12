package nl.inl.blacklab.search.results;

/**
 * Information about a results window.
 * 
 * For example: start and (requested/actual) size of the window 
 * and whether there are next/previous windows.
 */
public class WindowStats {
    
    private final int first;
    
    private final int actualWindowSize;
    
    private final int requestedWindowSize;

    private boolean hasNext;

    public WindowStats(boolean hasNext, int first, int requestedWindowSize, int actualWindowSize) {
        this.first = first;
        this.hasNext = hasNext;
        this.actualWindowSize = actualWindowSize;
        this.requestedWindowSize = requestedWindowSize;
    }

    public boolean hasNext() {
        return hasNext;
    }

    public boolean hasPrevious() {
        return first > 0;
    }

    public int nextFrom() {
        return first + actualWindowSize;
    }

    public int prevFrom() {
        return first - 1;
    }

    public int first() {
        return first;
    }

    public int last() {
        return first + actualWindowSize - 1;
    }

    public int windowSize() {
        return actualWindowSize;
    }

    public int requestedWindowSize() {
        return requestedWindowSize;
    }
}
