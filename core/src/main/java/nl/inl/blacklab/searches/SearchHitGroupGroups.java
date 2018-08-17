package nl.inl.blacklab.searches;

import java.util.function.Predicate;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * A search operation that yields groups of groups of hits as a result.
 * 
 * For example: documents grouped by author, while document itself is a group of hits.
 */
public abstract class SearchHitGroupGroups extends AbstractSearch {
    
    public SearchHitGroupGroups(QueryInfo queryInfo) {
        super(queryInfo);
        // TODO Auto-generated constructor stub
    }

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
    public abstract SearchHitGroupGroups observe(SearchResultObserver receiver);
    
    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public abstract SearchHitGroupGroups sortBy(ResultProperty<DocGroup> sortBy);

    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public abstract SearchHitGroupGroups sample(SampleParameters par);

    /**
     * Filter hits.
     * 
     * @param test what hits to keep
     * @return resulting operation
     */
    public abstract SearchHitGroupGroups filter(Predicate<DocGroup> test);

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public abstract SearchHitGroupGroups window(int first, int number);

    /**
     * Count hits.
     * 
     * @return resulting operation
     */
    public abstract SearchCount count();

}
