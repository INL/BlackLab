package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.search.results.ResultsWindow;

class ResultsWindowImpl implements ResultsWindow {
    private final int first;
    private final int totalResults;
    private final int actualWindowSize;
    private final int requestedWindowSize;

    ResultsWindowImpl(int totalResults, int first, int requestedWindowSize, int actualWindowSize) {
        this.first = first;
        this.totalResults = totalResults;
        this.actualWindowSize = actualWindowSize;
        this.requestedWindowSize = requestedWindowSize;
    }

    @Override
    public boolean hasNext() {
        return first + requestedWindowSize < totalResults;
    }

    @Override
    public boolean hasPrevious() {
        return first > 0;
    }

    @Override
    public int nextFrom() {
        return -1;
    }

    @Override
    public int prevFrom() {
        return -1;
    }

    @Override
    public int first() {
        return first;
    }

    @Override
    public int last() {
        return -1;
    }

    @Override
    public int size() {
        return actualWindowSize;
    }

    @Override
    public int sourceSize() {
        return -1;
    }

    @Override
    public int sourceTotalSize() {
        return -1;
    }

    @Override
    public int requestedWindowSize() {
        return requestedWindowSize;
    }
}
