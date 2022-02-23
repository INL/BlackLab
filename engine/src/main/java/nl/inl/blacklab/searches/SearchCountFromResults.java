package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.MaxStats;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.ResultCount.CountType;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.ResultsStats;

/**
 * A search operation that yields a count as its result.
 * @param <T> result type, e.g. Hit
 */
public class SearchCountFromResults<T extends Results<?, ?>> extends SearchCount {

    /** Placeholder ResultsStats for when counting hasn't begun yet.
     *
     *  Will return 0 if the count object doesn't exist yet, and simply
     *  delegate after it has been created. */
    private static class ResultsStatsDelegate extends ResultsStats {

        private ResultsStats count;

        public void setRealCount(ResultsStats count) {
            this.count = count;
        }

        @Override
        public boolean processedAtLeast(int lowerBound) {
            return count == null ? lowerBound == 0 : count.processedAtLeast(lowerBound);
        }

        @Override
        public int processedTotal() {
            return count == null ? 0 : count.processedTotal();
        }

        @Override
        public int processedSoFar() {
            return count == null ? 0 : count.processedSoFar();
        }

        @Override
        public int countedSoFar() {
            return count == null ? 0 : count.countedSoFar();
        }

        @Override
        public int countedTotal() {
            return count == null ? 0 : count.countedTotal();
        }

        @Override
        public boolean done() {
            return count == null ? false : count.done();
        }

        @Override
        public MaxStats maxStats() {
            return count == null ? MaxStats.NOT_EXCEEDED : count.maxStats();
        }

        @Override
        public String toString() {
            return "ResultsStatsDelegate(" + (count == null ? "no count yet" : count.toString()) + ")";
        }
    }

    /**
     * The search we're doing a count for.
     */
    private final SearchForResults<T> source;

    /**
     * Type of count we want (number of hits or docs).
     */
    private final CountType type;

    /**
     * The (running or finished) result count.
     * We can peek at this while it's running, or wait for executeInternal() to
     * complete, returning the final results.
     */
    private ResultsStatsDelegate resultCount;

    public SearchCountFromResults(QueryInfo queryInfo, SearchForResults<T> source, CountType type) {
        super(queryInfo);
        this.source = source;
        this.type = type;

        // Make sure we can peek at the count right away
        resultCount = new ResultsStatsDelegate();
    }

    @Override
    public ResultsStats executeInternal() throws InvalidQuery {
        // Start the search and construct the count object
        ResultsStats actualCount = new ResultCount(source.executeNoQueue(), type);

        // Update our ResultsStatsDelegate to report the actual count from now on
        resultCount.setRealCount(actualCount);

        // Gather all the hits.
        // This runs synchronously, so SearchCountFromResults will not be finished until
        // the entire count it finished. You can peek at the running count in the meantime,
        // however.
        resultCount.processedTotal();

        return resultCount;
    }

    /**
     * Peek at the running count.
     *
     * @return running count
     */
    @Override
    public ResultsStats peek() {
        return resultCount;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        @SuppressWarnings("unchecked")
        SearchCountFromResults<T> other = (SearchCountFromResults<T>) obj;
        if (source == null) {
            if (other.source != null)
                return false;
        } else if (!source.equals(other.source))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return toString("countfromresults", source, type);
    }

}
