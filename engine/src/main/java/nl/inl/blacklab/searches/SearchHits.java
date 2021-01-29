package nl.inl.blacklab.searches;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;

/** A search that yields hits. */
public abstract class SearchHits extends SearchResults<Hits> {

    public SearchHits(QueryInfo queryInfo) {
        super(queryInfo);
    }
    
    /**
     * Group hits by document.
     * 
     * This is a special case because it takes advantage of the fact that Lucene
     * returns results per document, so we don't have to fetch all hits to produce
     * document results.
     * 
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public SearchDocs docs(int maxResultsToGatherPerGroup) {
        return new SearchDocsFromHits(queryInfo(), this, maxResultsToGatherPerGroup);
    }

    /**
     * Group hits by a property.
     * 
     * @param groupBy what to group by
     * @param maxResultsToGatherPerGroup how many results to gather per group
     * @return resulting operation
     */
    public SearchHitGroups group(HitProperty groupBy, int maxResultsToGatherPerGroup) {
        return new SearchHitGroupsFromHits(queryInfo(), this, groupBy, maxResultsToGatherPerGroup);
    }

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    public SearchHits sort(HitProperty sortBy) {
        if (sortBy == null)
            return this;
        return new SearchHitsSorted(queryInfo(), this, sortBy);
    }
    
    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    public SearchHits sample(SampleParameters par) {
        return new SearchHitsSampled(queryInfo(), this, par);
    }

    /**
     * Get hits with a certain property value.
     * 
     * @param property property to test 
     * @param value value to test for
     * @return resulting operation
     */
    public SearchHits filter(HitProperty property, PropertyValue value) {
        return new SearchHitsFiltered(queryInfo(), this, property, value);
    }

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    public SearchHits window(int first, int number) {
        return new SearchHitsWindow(queryInfo(), this, first, number);
    }
    
    /**
     * Count words occurring near these hits.
     * 
     * @param annotation the property to use for the collocations (must have a
     *            forward index)
     * @param size context size to use for determining collocations
     * @param sensitivity sensitivity settings
     * @return resulting operation
     */
    public SearchCollocations collocations(Annotation annotation, ContextSize size, MatchSensitivity sensitivity) {
        return new SearchCollocationsFromHits(queryInfo(), this, annotation, size, sensitivity);
    }

    /** Does this query represent all tokens in a set of documents (possibly the whole index)?
     * 
     * If so, we can often optimize subsequent operations by resolving them more intelligently.
     */
    public boolean isAnyTokenQuery() {
        return false;
    }

    protected Query getFilterQuery() {
        return null;
    }
    
    protected abstract SearchSettings searchSettings();
}
