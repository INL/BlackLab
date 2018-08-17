package nl.inl.blacklab.searches;

import java.util.function.Predicate;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * Search operation that produces a list of results (e.g. Hit).
 * 
 * @param <R> type of result, e.g. Hit
 * @param <T> type of results, e.g. Hits 
 */
public abstract class SearchForResults<R, T extends Results<R>> extends AbstractSearch<T> {

	/**
	 * Execute the search operation, returning the final response.
	 *  
	 * @return result of the operation
	 */
	@Override
	public abstract T execute();

	/**
	 * Report the intermediate result.
	 * 
	 * @param receiver who to report to
	 * @return resulting operation
	 */
	@Override
	public abstract SearchForResults<R, T> custom(SearchOperation<T> receiver);
	
//	/**
//	 * Group hits by a property.
//	 * 
//	 * @param groupBy what to group by
//	 * @param maxResultsToGatherPerGroup how many results to gather per group
//	 * @return resulting operation
//	 */
//	public abstract <G extends Result<G>> SearchForResults<G, ResultGroups<G>> groupBy(ResultProperty<T> groupBy, int maxResultsToGatherPerGroup);

	/**
	 * Sort hits.
	 * 
	 * @param sortBy what to sort by
	 * @return resulting operation
	 */
	public abstract SearchForResults<R, T> sortBy(ResultProperty<R> sortBy);

    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
	public abstract SearchForResults<R, T> sample(SampleParameters par);

    /**
     * Filter hits.
     * 
     * @param test what hits to keep
     * @return resulting operation
     */
    public abstract SearchForResults<R, T> filter(Predicate<R> test);

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public abstract SearchForResults<R, T> window(int first, int number);

    /**
     * Count hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public abstract SearchForCount count();

}
