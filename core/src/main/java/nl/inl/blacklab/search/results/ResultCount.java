package nl.inl.blacklab.search.results;

import nl.inl.blacklab.exceptions.InterruptedSearch;

public class ResultCount extends ResultsStats implements SearchResult {
    
    private ResultsStats count;
    
    private boolean wasInterrupted = false;

    public ResultCount(Results<?> count) {
        this.count = count.resultsStats();
        update();
    }

    private void update() {
        if (!count.isStatic() && count.done()) {
            // Disengage from the search object and save the totals.
            count = count.save();
        }
    }
    
    @Override
    public int processedSoFar() {
        update();
        try {
            return count.processedSoFar();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public int processedTotal() {
        update();
        try {
            return count.processedTotal();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public boolean processedAtLeast(int n) {
        update();
        try {
            return count.processedAtLeast(n);
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public int countedSoFar() {
        update();
        try {
            return count.countedSoFar();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public int countedTotal() {
        update();
        try {
            return count.countedTotal();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public boolean done() {
        update();
        try {
            return count.done();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public MaxStats maxStats() {
        update();
        try {
            return count.maxStats();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public int numberOfResultObjects() {
        return 1;
    }

    @Override
    public boolean wasInterrupted() {
        return wasInterrupted;
    }

}
