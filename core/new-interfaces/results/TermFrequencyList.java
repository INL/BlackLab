package nl.inl.blacklab.interfaces.results;

import java.util.function.Predicate;

/** A list of term frequencies, for example collocations. */
public interface TermFrequencyList extends Results<TermFrequency> {

    @Override
    default TermFrequencyList save() {
        return this; // already immutable
    }
    
    /**
     * Get the frequency of a specific token
     *
     * @param token the token to get the frequency for
     * @return the frequency
     */
    public long getFrequency(String token);

    public long getTotalFrequency();

    TermFrequencyList window(int first, int number);
    
    /**
     * Return a new Results<Result> object with these results sorted by the given property.
     * 
     * NOTE: Must be called on a pristine-sequential or random access instance.
     * Calling this will exhaust a sequential instance.
     * 
     * The returned instance is always random-access.
     * 
     * @param sort the result calculation (often just a property) to sort on
     * @param reverse whether to sort in reverse or not
     * @return a new Results<Result> object with the same results, sorted in the specified way
     */
    TermFrequencyList sortedBy(ResultProperty<TermFrequency> sort, boolean reverse);
    
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
    TermFrequencyList filteredBy(Predicate<TermFrequency> test);
    
    /**
     * Return a random sample of results.
     * 
     * @param amount how much to sample
     * @return random sample
     */
    TermFrequencyList sample(SampleParameters amount);

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
    Groups<? extends TermFrequency, ? extends Group<TermFrequency>> groupedBy(final ResultProperty<TermFrequency> criteria, int maxResultsToGatherPerGroup);
    
    default Groups<? extends TermFrequency, ? extends Group<TermFrequency>> groupedBy(final ResultProperty<TermFrequency> criteria) {
        return groupedBy(criteria, NO_LIMIT);
    }
    
}
