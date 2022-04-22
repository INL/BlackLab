package nl.inl.blacklab.search.results;

/**
 * Information about a results window.
 * 
 * For example: start and (requested/actual) size of the window 
 * and whether there are next/previous windows.
 */
public class WindowStats {
    
    private final long first;
    
    private final long actualWindowSize;
    
    private final long requestedWindowSize;

    private final boolean hasNext;

    public WindowStats(boolean hasNext, long first, long requestedWindowSize, long actualWindowSize) {
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

    public long nextFrom() {
        return first + actualWindowSize;
    }

    public long prevFrom() {
        return first - 1;
    }

    public long first() {
        return first;
    }

    public long last() {
        return first + actualWindowSize - 1;
    }

    public long windowSize() {
        return actualWindowSize;
    }

    public long requestedWindowSize() {
        return requestedWindowSize;
    }
}
