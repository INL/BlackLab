package nl.inl.blacklab.searches;

import java.util.function.Predicate;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * A search operation that yields groups of groups of hits as a result.
 * 
 * For example: documents grouped by author, while document itself is a group of hits.
 */
public abstract class SearchForHitGroupGroups extends SearchForResults<DocGroup, DocGroups> {
    
    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     */
    @Override
    public abstract DocGroups execute();

    /**
     * Report the intermediate result.
     * 
     * @param receiver who to report to
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroupGroups custom(SearchOperation<DocGroups> receiver);
    
//    /**
//     * Group hits by a property.
//     * 
//     * @param groupBy what to group by
//     * @param maxResultsToGatherPerGroup how many results to gather per group
//     * @return resulting operation
//     */
//    @Override
//    public abstract SearchForResults<? extends Group<Group<HitGroup>>> groupBy(ResultProperty<Group<HitGroup>> groupBy, int maxResultsToGatherPerGroup);
    
    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroupGroups sortBy(ResultProperty<DocGroup> sortBy);

    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroupGroups sample(SampleParameters par);

    /**
     * Filter hits.
     * 
     * @param test what hits to keep
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroupGroups filter(Predicate<DocGroup> test);

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    @Override
    public abstract SearchForHitGroupGroups window(int first, int number);

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
