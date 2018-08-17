package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsFromHits extends SearchHitGroups {
    
    private SearchHits hitsSearch;
    
    private HitProperty groupBy;
    
    private int maxResultsToStorePerGroup;

    public SearchHitGroupsFromHits(QueryInfo queryInfo, List<SearchOperation> ops, SearchHits hitsSearch, HitProperty groupBy, int maxResultsToStorePerGroup) {
        super(queryInfo, ops);
        this.hitsSearch = hitsSearch;
        this.groupBy = groupBy;
        this.maxResultsToStorePerGroup = maxResultsToStorePerGroup;
    }

    /**
     * Execute the search operation, returning the final response.
     *  
     * @return result of the operation
     * @throws RegexpTooLarge if a regular expression was too large 
     * @throws WildcardTermTooBroad if a term expression matched too many terms
     */
    @Override
    public HitGroups execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return performCustom(HitGroups.fromHits(hitsSearch.execute(), groupBy, maxResultsToStorePerGroup));
    }

    @Override
    public SearchHitGroupsFromHits custom(SearchOperation operation) {
        return new SearchHitGroupsFromHits(queryInfo(), extraCustomOp(operation), hitsSearch, groupBy, maxResultsToStorePerGroup);
    }
    
//    /**
//     * Group hits by a property.
//     * 
//     * @param groupBy what to group by
//     * @param maxResultsToGatherPerGroup how many results to gather per group
//     * @return resulting operation
//     */
//    public SearchForHitGroupGroups groupBy(ResultProperty<HitGroup> groupBy, int maxResultsToGatherPerGroup);

//
//    /**
//     * Sample hits.
//     * 
//     * @param par how many hits to sample; seed
//     * @return resulting operation
//     */
//    public SearchForHitGroups sample(SampleParameters par);
//
//    /**
//     * Filter hits.
//     * 
//     * @param test what hits to keep
//     * @return resulting operation
//     */
//    public SearchForHitGroups filter(Predicate<HitGroup> test);
//
//    /**
//     * Get window of hits.
//     * 
//     * @param first first hit to select
//     * @param number number of hits to select
//     * @return resulting operation
//     */
//    public SearchForHitGroups window(int first, int number);
//
//    /**
//     * Count hits.
//     * 
//     * @return resulting operation
//     */
//    public SearchForCount count();

}
