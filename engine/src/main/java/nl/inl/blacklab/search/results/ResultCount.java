package nl.inl.blacklab.search.results;

import nl.inl.blacklab.exceptions.InterruptedSearch;

public class ResultCount extends ResultsStats implements SearchResult {

    public enum CountType {
        RESULTS, // number of results
        HITS,    // number of hits represented by results
        DOCS     // number of docs represented by results
    }

    private ResultsStats count;

    private boolean wasInterrupted = false;

    public ResultCount(Results<?, ?> count, CountType type) {
        switch (type) {
        case RESULTS:
            this.count = count.resultsStats();
            break;
        case HITS:
            if (count instanceof Hits) {
                this.count = ((Hits) count).hitsStats();
            } else if (count instanceof HitGroups) {
                int n = ((HitGroups) count).sumOfGroupSizes();
                this.count = new ResultsStatsStatic(n, n, MaxStats.NOT_EXCEEDED);
            } else if (count instanceof DocResults) {
                int n = ((DocResults) count).sumOfGroupSizes();
                this.count = new ResultsStatsStatic(n, n, MaxStats.NOT_EXCEEDED);
            } else if (count instanceof DocGroups) {
                throw new UnsupportedOperationException("Cannot get hits count from DocGroups");
            }
            break;
        case DOCS:
            if (count instanceof Hits) {
                this.count = ((Hits) count).docsStats();
            } else if (count instanceof HitGroups) {
                throw new UnsupportedOperationException("Cannot get docs count from HitGroups");
            } else if (count instanceof DocResults) {
                this.count = count.resultsStats();
            } else if (count instanceof DocGroups) {
                int n = ((DocGroups) count).sumOfGroupSizes();
                this.count = new ResultsStatsStatic(n, n, MaxStats.NOT_EXCEEDED);
            }
            break;
        }
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

    @Override
    public String toString() {
        return "ResultCount [count=" + count + ", wasInterrupted=" + wasInterrupted + "]";
    }



}
