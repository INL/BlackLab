package nl.inl.blacklab.interfaces.search;

import java.util.function.Predicate;

import nl.inl.blacklab.interfaces.results.Group;
import nl.inl.blacklab.interfaces.results.ResultProperty;
import nl.inl.blacklab.interfaces.results.Results;
import nl.inl.blacklab.interfaces.results.SampleParameters;

/**
 * Search operation that produces a list of results (e.g. Hit).
 * 
 * @param <T> type of result 
 */
public abstract class SearchForResults<T> extends AbstractSearch {

	/**
	 * Execute the search operation, returning the final response.
	 *  
	 * @return result of the operation
	 */
	@Override
	public abstract Results<T> execute();

	/**
	 * Report the intermediate result.
	 * 
	 * @param receiver who to report to
	 * @return resulting operation
	 */
	@Override
	public abstract SearchForResults<T> custom(SearchOperation receiver);
	
	/**
	 * Group hits by a property.
	 * 
	 * @param groupBy what to group by
	 * @param maxResultsToGatherPerGroup how many results to gather per group
	 * @return resulting operation
	 */
	public abstract SearchForResults<? extends Group<T>> groupBy(ResultProperty<T> groupBy, int maxResultsToGatherPerGroup);

	/**
	 * Sort hits.
	 * 
	 * @param sortBy what to sort by
	 * @return resulting operation
	 */
	public abstract SearchForResults<T> sortBy(ResultProperty<T> sortBy);

    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
	public abstract SearchForResults<T> sample(SampleParameters par);

    /**
     * Filter hits.
     * 
     * @param test what hits to keep
     * @return resulting operation
     */
    public abstract SearchForResults<T> filter(Predicate<T> test);

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public abstract SearchForResults<T> window(int first, int number);

    /**
     * Count hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public abstract SearchForCount count();

}
