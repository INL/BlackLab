package nl.inl.blacklab.server.lib.results;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.search.SearchManager;

public class ResultHitsCsv {

    /**
     * Get the hits (and the groups from which they were extracted - if applicable)
     * or the groups for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the hits.
     *
     * @return Hits if looking at ungrouped hits, Hits+Groups if looking at hits
     *         within a group, Groups if looking at grouped hits.
     */
    // TODO share with regular RequestHandlerHits, allow configuring windows, totals, etc ?
    static ResultHitsCsv get(SearchCreator params, SearchManager searchMan) throws BlsException,
            InvalidQuery {
        // Might be null
        String groupBy = params.getGroupProps().orElse(null);
        String viewGroup = params.getViewGroup().orElse(null);
        String sortBy = params.getSortProps().orElse(null);

        SearchCacheEntry<?> cacheEntry;
        Hits hits;
        HitGroups groups = null;
        DocResults subcorpus = params.subcorpus().execute();

        try {
            if (!StringUtils.isEmpty(groupBy)) {
                hits = params.hitsSample().execute();
                groups = params.hitsGroupedWithStoredHits().execute();

                if (viewGroup != null) {
                    PropertyValue groupId = PropertyValue.deserialize(params.blIndex(), params.blIndex().mainAnnotatedField(), viewGroup);
                    if (groupId == null)
                        throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
                    HitGroup group = groups.get(groupId);
                    if (group == null)
                        throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                    hits = group.storedResults();

                    // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                    // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                    // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                    // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
                    if (sortBy != null) {
                        HitProperty sortProp = HitProperty.deserialize(hits, sortBy);
                        if (sortProp == null)
                            throw new BadRequest("ERROR_IN_SORT_VALUE", "Cannot deserialize sort value: " + sortBy);
                        hits = hits.sort(sortProp);
                    }
                }
            } else {
                // Use a regular search for hits, so that not all hits are actually retrieved yet, we'll have to construct a pagination view on top of the hits manually
                cacheEntry = params.hitsSample().executeAsync();
                hits = (Hits) cacheEntry.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        // apply window settings
        // Different from the regular results, if no window settings are provided, we export the maximum amount automatically
        // The max for CSV exports is also different from the default pagesize maximum.
        if (hits != null) {
            long first = Math.max(0, params.getFirstResultToShow()); // Defaults to 0
            if (!hits.hitsStats().processedAtLeast(first))
                first = 0;

            long number = searchMan.config().getSearch().getMaxHitsToRetrieve();
            if (params.optNumberOfResultsToShow().isPresent()) {
                long requested = params.optNumberOfResultsToShow().get();
                if (number >= 0 || requested >= 0) { // clamp
                    number = Math.min(requested, number);
                }
            }

            if (number >= 0)
                hits = hits.window(first, number);
        }

        List<Annotation> annotationsToWrite = WebserviceOperations.getAnnotationsToWrite(params);
        return new ResultHitsCsv(params, hits, groups, subcorpus, viewGroup != null, annotationsToWrite);
    }

    private SearchCreator params;

    private final Hits hits;

    private final HitGroups groups;

    private final DocResults subcorpusResults;

    private final boolean isViewGroup;

    private final List<Annotation> annotationsToWrite;

    ResultHitsCsv(SearchCreator params, Hits hits, HitGroups groups, DocResults subcorpusResults, boolean isViewGroup,
            List<Annotation> annotationsToWrite) {
        super();
        this.params = params;
        this.hits = hits;
        this.groups = groups;
        this.subcorpusResults = subcorpusResults;
        this.isViewGroup = isViewGroup;
        this.annotationsToWrite = annotationsToWrite;
    }

    public Hits getHits() {
        return hits;
    }

    public HitGroups getGroups() {
        return groups;
    }

    public DocResults getSubcorpusResults() {
        return subcorpusResults;
    }

    public boolean isViewGroup() {
        return isViewGroup;
    }

    public List<Annotation> getAnnotationsToWrite() {
        return annotationsToWrite;
    }

    public SearchCreator getParams() {
        return params;
    }
}
