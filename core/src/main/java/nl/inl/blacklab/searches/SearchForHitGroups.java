package nl.inl.blacklab.searches;

import java.util.function.Predicate;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * A search operation that yields groups of hits.
 */
public abstract class SearchForHitGroups extends SearchForResults<HitGroup, HitGroups> {
    
    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     */
    @Override
    public abstract HitGroups execute();

    /**
     * Report the intermediate result.
     * 
     * @param receiver who to report to
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroups custom(SearchOperation<HitGroups> receiver);
    
    /**
     * Group hits by a property.
     * 
     * @param groupBy what to group by
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public abstract SearchForHitGroupGroups groupBy(ResultProperty<HitGroup> groupBy, int maxResultsToGatherPerGroup);

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroups sortBy(ResultProperty<HitGroup> sortBy);

    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroups sample(SampleParameters par);

    /**
     * Filter hits.
     * 
     * @param test what hits to keep
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroups filter(Predicate<HitGroup> test);

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroups window(int first, int number);

    /**
     * Count hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    @Override
    public abstract SearchForCount count();

}
