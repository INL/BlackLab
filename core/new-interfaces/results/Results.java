package nl.inl.blacklab.interfaces.results;

import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A collection of results of some type (e.g. Hit or Doc), which
 * may be random-access or 
 *
 * Comes in two flavours: sequential or random access. Random access instances allow you
 * to access any result at any time, while sequential instances only allow sequential
 * access.
 * 
 * Random access instances are immutable. Sequential instances are not, with respect to 
 * their results: their mutable state is the results they have already seen and cannot go back to.
 * 
 * A sequential instance is said to be pristine if none of its results have been seen;
 * it is said to be exhausted if all of its results have been seen, or the maximum number
 * of results to process was reached.
 * 
 * @param <T> type of results
 */
public interface Results<T> extends Iterable<T>, SearchResult {
	
	/**
	 * A value indicating there is no limit (typically on the number of results gathered).
	 */
	int NO_LIMIT = -1;
	
    /**
     * Pause/resume gathering of results, if applicable.
     * 
     * This is used to prevent heavy queries from demanding all available CPU 
     * while other users are waiting. The query can resume when other queries have had
     * a chance to run.
     *  
     * @param b true if we want to pause gathering of results
     */
    void pause(boolean b);
    
	// Streaming results sequentially
	// ===============================================================
	// Applications must be able to apply streaming operations on results, ideally in parallel.
	
	/**
	 * Iterate over results sequentially.
	 * 
	 * @return results iterator
	 */
	@Override
	Iterator<T> iterator();
	
	/**
	 * Stream all results sequentially.
	 * 
	 * NOTE: Must be called on a pristine-sequential or random access instance.
	 * Calling this will exhaust a sequential instance.
	 * 
	 * Assume a streamed result to be ephemeral. If you wish to store it, call T.save() to get
	 * an immutable copy.
	 * 
	 * @return stream of results
	 */
	Stream<T> stream();
	
	
	// Random-access related methods
	// ===============================================================
	// Applications must be able to get random access to Results<T> objects,
	// even if they have to wrap them to do so.
	
	/**
	 * Can we access any result in this instance at any time?
	 * 
	 * @return true if we can, false if we can only access results sequentially
	 */
	boolean isRandomAccess();
	
	/**
	 * Return an immutable, random-access version of this instance.
	 * 
	 * NOTE: Must be called on a pristine-sequential or random access instance.
	 * Calling this on a sequential instance will exhaust it. Calling this on a random 
	 * access instance will simply return itself.
	 * 
	 * @return random-access version of this instance.
	 */
	Results<T> withRandomAccess();
	
	@Override
	default Results<T> save() {
	    // random-access Results are (effectively) immutable,
	    // sequential ones are not
	    return withRandomAccess();
	}
	
	/**
	 * Get a specific result from this instance.
	 * 
	 * Only works for random access instances.
	 * 
	 * The returned result may be ephemeral. If you wish to store it, call 
	 * T.save() to get an immutable copy.
	 * 
	 * @param index result index to get (0-based)
	 * @return the result
	 */
	T get(int index);
	
	// Methods for deriving other (Results<T>) objects
	// ===============================================================
	// Applications must be able to apply actions on Results<T> objects to sort,
	// filter and group them, resulting in a new Results<T> object with the desired
	// properties.
	
	/**
	 * Return part of the results represented by this Results<T> object.
	 * 
	 * NOTE: this will (partially) exhaust sequential instances,
	 * but they can be used afterwards to fetch results occurring after this window.
	 * 
	 * Calling this on a sequential instance to fetch a window containing
	 * results this class has already seen will throw an exception. In that case,
	 * you must create a new Results<T> object and iterate to the point you need.
	 * 
	 * Returns null if the first result you request doesn't exist, or is larger than 
	 * the maximum number of results we process. This is the 'proper' way of discovering
	 * whether or not a window is valid or not (unless you've already determined the 
	 * total number of results ealier, using another Results<T> object)
	 * 
	 * The returned instance is always random-access.
	 * 
	 * @param first first result to select (0-based)
	 * @param number number of results to select
	 * @return window of results, or null if the first result doesn't exist or goes beyond
	 *   our maximum number of results to process
	 */
	Results<T> window(int first, int number);
	
	/**
	 * Return a new Results<T> object with these results sorted by the given property.
	 * 
	 * NOTE: Must be called on a pristine-sequential or random access instance.
	 * Calling this will exhaust a sequential instance.
	 * 
	 * The returned instance is always random-access.
	 * 
	 * @param sort the result calculation (often just a property) to sort on
	 * @param reverse whether to sort in reverse or not
	 * @return a new Results<T> object with the same results, sorted in the specified way
	 */
	Results<T> sortedBy(ResultProperty<T> sort, boolean reverse);
	
	/**
	 * Select only the results where the specified property has the specified value.
	 * 
	 * NOTE: Must be called on a pristine-sequential or random access instance.
	 * Calling this will effectively exhaust a sequential instance.
	 * 
	 * The returned instance is random access if an only if this instance
	 * was.
	 * 
	 * @param property property to select on, e.g. "word left of result"
	 * @param test predicate that decides whether a result matches or not
	 * @return filtered results
	 */
	Results<T> filteredBy(Predicate<T> test);
	
	/**
	 * Return a random sample of results.
	 * 
	 * @param amount how much to sample
	 * @return random sample
	 */
	Results<T> sample(SampleParameters amount);

	/**
	 * Group these results by a criterium (or several criteria).
	 * 
	 * NOTE: Must be called on a pristine-sequential or random access instance.
	 * Calling this will exhaust a sequential instance.
	 * 
	 * The results gathered per group (if any) are random-access instances.
	 * 
	 * NOTE: we should probably differentiate between grouping and wanting
	 * access to (some of) the results in each group, and just being interested 
	 * in the end result.
	 *
	 * TODO: ideally, there wouldn't be a fundamental difference between grouping
	 * by document and other groupings; internally it would be optimized (results
	 * are in document order when they are produced by Lucene, after all), but
	 * the client doesn't need to know about that. Furthermore, regular grouping should
	 * allow us to only store a few of the results in the group (normally the first few, 
	 * or some other selection criterium?), so we can choose if and how many snippets
	 * we want to see per document.
	 * Problem with this approach is that you want to be able to group document results,
	 * so if document results are HitGroups, we need a way to group HitGroups based on a 
	 * HitGroupProperty or something. Not necessarily a bad idea though.
	 * Another problem is that DocResults have additional data per "group" (document):
	 * the document's metadata. Maybe we can subclass HitGroups to get this?
	 *
	 * @param criteria the result property/properties to group on
	 * @param maxResultsToGatherPerGroup how many results to gather per group at most, or 
	 *   NO_LIMIT for all
	 * @return a HitGroups object representing the grouped results
	 */
	Groups<? extends T, ? extends Group<T>> groupedBy(final ResultProperty<T> criteria, int maxResultsToGatherPerGroup);
	
	default Groups<? extends T, ? extends Group<T>> groupedBy(final ResultProperty<T> criteria) {
		return groupedBy(criteria, NO_LIMIT);
	}
    
	
	// Methods for getting information about the number of results
	// ===============================================================
	// Applications must be able to get information about progress of fetching results
	// and the final results, and should be able to control how many results are fetched.
	// (these methods may be called regardless of the "pristine"/"exhausted" state)
	
	/**
	 * Returns number of results processed so far in this Results instance.
	 * 
	 * Shortcut method to the stats method(s) each Results-derived interface has.
	 * 
	 * E.g. for Hits, this would be equivalent to stats().hits().processed();
	 * for Docs, to stats().docs().processed().
	 * 
	 * @return number of results processed so far
	 */
	ResultsNumber size();

	/**
	 * If this is a window into a larger results set, what's the range?
	 * 
	 * @return range, or null if this is not a window
	 */
    ResultsWindowStats range();
	
	/**
	 * Are all results still reachable for this instance?
	 * 
	 * For a random access instance, always returns true.
	 * For a sequential instance, returns true only if no results have
	 * been seen yet.
	 * 
	 * @return true if and only if all results are still reachable
	 */
	default boolean isPristine() {
		return isRandomAccess() || size().soFar() == 0;
	}
	
}
