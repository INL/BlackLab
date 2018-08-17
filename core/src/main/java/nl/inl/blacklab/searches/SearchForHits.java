package nl.inl.blacklab.searches;

import java.util.function.Predicate;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * Search operation that produces Hits
 */
public abstract class SearchForHits extends SearchForResults<Hit, Hits> {

    /**
     * Execute the search operation, returning the final response.
     * 
     * @return result of the operation
     */
    @Override
    public abstract Hits execute();

    /**
     * Perform a custom operation on the result.
     * 
     * This is useful to allow the client to put intermediate results
     * into the cache, among other things.
     * 
     * @param processor operation to perform
     * @return resulting operation
     */
    @Override
    public abstract SearchForHits custom(SearchOperation<Hits> processor);

    /**
     * Group hits by a property.
     * 
     * @param groupBy what to group by
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public abstract SearchForHitGroups groupBy(ResultProperty<Hit> groupBy, int maxResultsToGatherPerGroup);

    /**
     * Group by document.
     * 
     * Convenience method.
     * 
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public abstract SearchForHitGroups groupByDocument(int maxResultsToGatherPerGroup);

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    @Override
    public abstract SearchForHits sortBy(ResultProperty<Hit> sortBy);

    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    @Override
    public abstract SearchForHits sample(SampleParameters par);

    /**
     * Filter hits.
     * 
     * @param test what hits to keep
     * @return resulting operation
     */
    @Override
    public abstract SearchForHits filter(Predicate<Hit> test);

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    @Override
    public abstract SearchForHits window(int first, int number);

    /**
     * Count hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    @Override
    public abstract SearchForCount count();

    /**
     * Count words occurring near these hits.
     * 
     * @param annotation the property to use for the collocations (must have a
     *            forward index)
     * @param size context size to use for determining collocations
     * @param sensitivity sensitivity settings
     * @return resulting operation
     */
    public abstract SearchForCollocations collocations(Annotation annotation, ContextSize size,
            MatchSensitivity sensitivity);

}
