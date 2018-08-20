package nl.inl.blacklab.search.results;

public class ResultCount extends ResultsStats implements SearchResult {
    
    private ResultsStats count;

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
        return count.processedSoFar();
    }

    @Override
    public int processedTotal() {
        update();
        return count.processedTotal();
    }

    @Override
    public boolean processedAtLeast(int n) {
        update();
        return count.processedAtLeast(n);
    }

    @Override
    public int countedSoFar() {
        update();
        return count.countedSoFar();
    }

    @Override
    public int countedTotal() {
        update();
        return count.countedTotal();
    }

    @Override
    public boolean done() {
        update();
        return count.done();
    }
}
