package nl.inl.blacklab.searches;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultsStats;

import java.util.concurrent.Future;

/**
 * A search operation that yields a count as its result.
 */
public abstract class SearchCount extends AbstractSearch<ResultsStats> {
    
    public SearchCount(QueryInfo queryInfo) {
        super(queryInfo);
    }

    /**
     * Peek at the running count.
     *
     * @return running count
     */
    @Override
    public abstract ResultsStats peek(Future<ResultsStats> task);

}
