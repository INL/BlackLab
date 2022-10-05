package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.search.SearchManager;

public class ResultDocsResponse {
    private Collection<Annotation> annotationsTolist;
    private Collection<MetadataField> metadataFieldsToList;
    private BlackLabIndex index;
    private boolean includeTokenCount;
    private long totalTokens;
    private ResultSummaryCommonFields summaryFields;
    private ResultSummaryNumDocs numResultDocs;
    private ResultSummaryNumHits numResultHits;
    private final DocResults window;
    private final SearchCreator params;

    ResultDocsResponse(Collection<Annotation> annotationsTolist, Collection<MetadataField> metadataFieldsToList,
            BlackLabIndex blIndex, boolean includeTokenCount, long totalTokens,
            ResultSummaryCommonFields summaryFields,
            ResultSummaryNumDocs numResultDocs, ResultSummaryNumHits numResultHits, DocResults window,
            SearchCreator params) {
        this.annotationsTolist = annotationsTolist;
        this.metadataFieldsToList = metadataFieldsToList;
        this.index = blIndex;
        this.includeTokenCount = includeTokenCount;
        this.totalTokens = totalTokens;
        this.summaryFields = summaryFields;
        this.numResultDocs = numResultDocs;
        this.numResultHits = numResultHits;
        this.window = window;
        this.params = params;
    }

    static ResultDocsResponse viewGroupDocsResponse(SearchCreator params,
            SearchManager searchMan, IndexManager indexMan) {
        String viewGroup = params.getViewGroup().get();

        // TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)

        SearchCacheEntry<DocGroups> docGroupFuture;
        // Yes. Group, then show hits from the specified group
        SearchCacheEntry<?> search = docGroupFuture = params.docsGrouped().executeAsync();
        DocGroups groups;
        try {
            groups = docGroupFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        PropertyValue viewGroupVal = PropertyValue.deserialize(groups.index(), groups.field(), viewGroup);
        if (viewGroupVal == null) {
            throw new BadRequest("ERROR_IN_GROUP_VALUE", "Parameter 'viewgroup' has an illegal value: " + viewGroup);
        }

        DocGroup group = groups.get(viewGroupVal);
        if (group == null)
            throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need
        // some more testing to see if everything is correct if we change this
        String sortBy = params.getSortProps().orElse("");
        DocResults docsSorted = group.storedResults();
        DocProperty sortProp = DocProperty.deserialize(params.blIndex(), sortBy);
        if (sortProp != null)
            docsSorted = docsSorted.sort(sortProp);

        long first = params.getFirstResultToShow();
        if (first < 0)
            first = 0;
        long number = params.getNumberOfResultsToShow();
        if (number < 0 || number > searchMan.config().getParameters().getPageSize().getMax())
            number = searchMan.config().getParameters().getPageSize().getDefaultValue();
        DocResults totalDocResults = docsSorted;
        DocResults window = docsSorted.window(first, number);

        DocResults docResults = group.storedResults();
        long totalTime = docGroupFuture.timer().time();

        return docsResponse(params, totalDocResults, docResults, window, search, null, indexMan,
                true, WebserviceOperations.getAnnotationsToWrite(params),
                WebserviceOperations.getMetadataToWrite(params), true,
                totalTime);
    }

    static ResultDocsResponse regularDocsResponse(SearchCreator params, IndexManager indexMan) {
        // Make sure we have the hits search, so we can later determine totals.
        SearchCacheEntry<ResultsStats> originalHitsSearch = null;
        if (params.hasPattern()) {
            originalHitsSearch = params.hitsSample().hitCount().executeAsync();
        }

        SearchCacheEntry<DocResults> searchWindow = params.docsWindow().executeAsync();
        SearchCacheEntry<?> search = searchWindow;

        // Also determine the total number of hits
        SearchCacheEntry<DocResults> total = params.docs().executeAsync();

        DocResults window, totalDocResults;
        try {
            window = searchWindow.get();
            totalDocResults = total.get();
        } catch (InterruptedException | ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        // If "waitfortotal=yes" was passed, block until all results have been fetched
        boolean waitForTotal = params.getWaitForTotal();
        if (waitForTotal)
            totalDocResults.size();

        DocResults docResults = totalDocResults;
        long totalTime = total.threwException() ? 0 : total.timer().time();

        return docsResponse(params, totalDocResults, docResults, window, search, originalHitsSearch, indexMan,
                false, WebserviceOperations.getAnnotationsToWrite(params),
                WebserviceOperations.getMetadataToWrite(params), waitForTotal,
                totalTime);
    }

    private static ResultDocsResponse docsResponse(
            SearchCreator params,
            DocResults totalDocResults,
            DocResults docResults,
            DocResults window,
            SearchCacheEntry<?> search,
            SearchCacheEntry<ResultsStats> originalHitsSearch,
            IndexManager indexMan,
            boolean isViewGroup,
            Collection<Annotation> annotationsTolist,
            Collection<MetadataField> metadataFieldsToList,
            boolean waitForTotal,
            long totalTime) {

        BlackLabIndex index = params.blIndex();
        boolean includeTokenCount = params.getIncludeTokenCount();
        long totalTokens = -1;
        if (includeTokenCount) {
            // Determine total number of tokens in result set
            totalTokens = totalDocResults.subcorpusSize().getTokens();
        }

        ResultsStats hitsStats, docsStats;
        hitsStats = originalHitsSearch == null ? null : originalHitsSearch.peek();
        docsStats = params.docsCount().executeAsync().peek();
        SearchTimings timings = new SearchTimings(search.timer().time(), totalTime);
        Index.IndexStatus indexStatus = indexMan.getIndex(params.getIndexName()).getStatus();
        ResultSummaryCommonFields summaryFields = WebserviceOperations.summaryCommonFields(params, indexStatus, timings,
                null, window.windowStats());
        boolean countFailed = totalTime < 0;
        ResultSummaryNumDocs numResultDocs = null;
        ResultSummaryNumHits numResultHits = null;
        if (hitsStats == null) {
            numResultDocs = WebserviceOperations.numResultsSummaryDocs(isViewGroup,
                    docResults, countFailed, null);
        } else {
            numResultHits = WebserviceOperations.numResultsSummaryHits(
                    hitsStats, docsStats, waitForTotal, countFailed, null);
        }

        // Search is done; construct the results object

        ResultDocsResponse result = new ResultDocsResponse(annotationsTolist, metadataFieldsToList, index,
                includeTokenCount,
                totalTokens, summaryFields, numResultDocs, numResultHits, window, params);
        return result;
    }

    public Collection<Annotation> getAnnotationsTolist() {
        return annotationsTolist;
    }

    public Collection<MetadataField> getMetadataFieldsToList() {
        return metadataFieldsToList;
    }

    public BlackLabIndex getIndex() {
        return index;
    }

    public boolean isIncludeTokenCount() {
        return includeTokenCount;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public ResultSummaryCommonFields getSummaryFields() {
        return summaryFields;
    }

    public ResultSummaryNumDocs getNumResultDocs() {
        return numResultDocs;
    }

    public ResultSummaryNumHits getNumResultHits() {
        return numResultHits;
    }

    public DocResults getWindow() {
        return window;
    }

    public SearchCreator getParams() {
        return params;
    }
}
