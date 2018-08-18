package nl.inl.blacklab.searches;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * Search operation that yields collocations.
 */
public abstract class SearchCollocations extends AbstractSearch {

    public SearchCollocations(QueryInfo queryInfo) {
        super(queryInfo);
    }
    
    @Override
    public final TermFrequencyList execute() throws InvalidQuery {
        CompletableFuture<TermFrequencyList> future = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<TermFrequencyList> fromCache = (CompletableFuture<TermFrequencyList>)getFromCache(this, future);
        if (fromCache != null) {
            try {
                return fromCache.get();
            } catch (InterruptedException | ExecutionException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
        try {
            TermFrequencyList result = executeInternal();
            future.complete(result);
            return result;
        } catch (InvalidQuery e) {
            cancelSearch(future);
            throw e;
        } catch (RuntimeException e) {
            cancelSearch(future);
            throw e;
        }
    }

    protected abstract TermFrequencyList executeInternal() throws InvalidQuery;

    /**
     * Sort collocations.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchCollocations sortBy(ResultProperty<TermFrequency> sortBy) {
        return new SearchCollocationsSorted(queryInfo(), this, sortBy);
    }

    /**
     * Sample collocations.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchCollocations sample(SampleParameters par) {
        return new SearchCollocationsSampled(queryInfo(), this, par);
    }

    /**
     * Filter collocations.
     * 
     * @param property property to filter on 
     * @param value value to keep
     * @return resulting operation
     */
    public SearchCollocations filter(ResultProperty<TermFrequency> property, PropertyValue value) {
        return new SearchCollocationsFiltered(queryInfo(), this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchCollocations window(int first, int number) {
        return new SearchCollocationsWindow(queryInfo(), this, first, number);
    }
}
