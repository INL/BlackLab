package nl.inl.blacklab.interfaces.search;

import java.util.function.Predicate;

import nl.inl.blacklab.interfaces.results.HitGroup;
import nl.inl.blacklab.interfaces.results.HitGroups;
import nl.inl.blacklab.interfaces.results.ResultProperty;
import nl.inl.blacklab.interfaces.results.SampleParameters;

/**
 * A search operation that yields groups of hits.
 */
public abstract class SearchForHitGroups extends SearchForResults<HitGroup> {
    
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
    public abstract SearchForHitGroups custom(SearchOperation receiver);
    
    /**
     * Group hits by a property.
     * 
     * @param groupBy what to group by
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    @Override
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
