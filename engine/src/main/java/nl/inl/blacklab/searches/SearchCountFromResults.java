package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.ResultCount.CountType;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.ResultsStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A search operation that yields a count as its result.
 * @param <T> result type, e.g. Hit
 */
public class SearchCountFromResults<T extends Results<?, ?>> extends SearchCount {

    private static final Logger logger = LogManager.getLogger(SearchCountFromResults.class);
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
    private ResultsStats resultCount;

    /**
     * Latch that marks the end of the search for this count.
     */
    private final CountDownLatch searchLatch;

    /**
     * Maximum time to wait for a search before giving up
     */
    private final int MAX_SEARCH_WAIT_IN_MS = 2000;

    public SearchCountFromResults(QueryInfo queryInfo, SearchForResults<T> source, CountType type) {
        super(queryInfo);
        this.source = source;
        this.type = type;
        this.searchLatch = new CountDownLatch(1);
    }

    @Override
    public ResultsStats executeInternal() throws InvalidQuery {
        // Start the search and construct the count object
        resultCount = new ResultCount(source.executeNoQueue(), type);
        // Mark the end of the search.
        searchLatch.countDown();

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
    public ResultsStats peek(Future<ResultsStats> searchTask) {
        if (resultCount != null) {
            return resultCount;
        }

        try {
            // Get the results from a finished task first.
            if (searchTask != null && searchTask.isDone()) {
                resultCount = searchTask.get();
                return resultCount;
            }

            // Otherwise, wait for the search to finish.
            boolean finished = searchLatch.await(MAX_SEARCH_WAIT_IN_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new InterruptedSearch("Could not finish searching for count");
            }
            return resultCount;
        } catch (ExecutionException e) {
            throw new InterruptedSearch(e);
        } catch (InterruptedException e) {
            throw new InterruptedSearch(e);
        }
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
