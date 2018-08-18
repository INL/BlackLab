package nl.inl.blacklab.searches;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that yields groups of hits. */
public abstract class SearchHitGroups extends SearchResults<HitGroup> {
    
    public SearchHitGroups(QueryInfo queryInfo) {
        super(queryInfo);
    }
    
    @Override
    public final HitGroups execute() throws InvalidQuery {
        CompletableFuture<HitGroups> future = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<HitGroups> fromCache = (CompletableFuture<HitGroups>)getFromCache(this, future);
        if (fromCache != null) {
            try {
                return fromCache.get();
            } catch (InterruptedException | ExecutionException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
        try {
            HitGroups result = executeInternal();
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
    
    protected abstract HitGroups executeInternal() throws InvalidQuery;

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchHitGroups sort(ResultProperty<HitGroup> sortBy) {
        return new SearchHitGroupsSorted(queryInfo(), this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchHitGroups sample(SampleParameters par) {
        return new SearchHitGroupsSampled(queryInfo(), this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchHitGroups filter(HitGroupProperty property, PropertyValue value) {
        return new SearchHitGroupsFiltered(queryInfo(), this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchHitGroups window(int first, int number) {
        return new SearchHitGroupsWindow(queryInfo(), this, first, number);
    }

}
