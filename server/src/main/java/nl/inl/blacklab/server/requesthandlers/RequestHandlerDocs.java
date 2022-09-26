package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.lib.ResultDocInfo;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceOperations;

/**
 * List documents, search for documents matching criteria.
 */
public class RequestHandlerDocs extends RequestHandler {

    public RequestHandlerDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    SearchCacheEntry<?> search = null;
    SearchCacheEntry<ResultsStats> originalHitsSearch;
    DocResults totalDocResults;
    DocResults window;
    private DocResults docResults;
    private long totalTime;

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        // Do we want to view a single group after grouping?
        String groupBy = params.getGroupProps().orElse("");
        String viewGroup = params.getViewGroup().orElse("");
        int response = 0;

        // Make sure we have the hits search, so we can later determine totals.
        originalHitsSearch = null;
        if (params.hasPattern()) {
            originalHitsSearch = params.hitsSample().hitCount().executeAsync();
        }

        if (groupBy.length() > 0 && viewGroup.length() > 0) {

            // View a single group in a grouped docs resultset
            response = doViewGroup(ds, viewGroup);

        } else {
            // Regular set of docs (no grouping first)
            response = doRegularDocs(ds);
        }
        return response;
    }

    private int doViewGroup(DataStream ds, String viewGroup) throws BlsException, InvalidQuery {
        // TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)

        SearchCacheEntry<DocGroups> docGroupFuture;
        // Yes. Group, then show hits from the specified group
        search = docGroupFuture = params.docsGrouped().executeAsync();
        DocGroups groups;
        try {
            groups = docGroupFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }

        PropertyValue viewGroupVal = null;
        viewGroupVal = PropertyValue.deserialize(groups.index(), groups.field(), viewGroup);
        if (viewGroupVal == null)
            return Response.badRequest(ds, "ERROR_IN_GROUP_VALUE",
                    "Parameter 'viewgroup' has an illegal value: " + viewGroup);

        DocGroup group = groups.get(viewGroupVal);
        if (group == null)
            return Response.badRequest(ds, "GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need
        // some more testing to see if everything is correct if we change this
        String sortBy = params.getSortProps().orElse("");
        DocResults docsSorted = group.storedResults();
        DocProperty sortProp = DocProperty.deserialize(blIndex(), sortBy);
        if (sortProp != null)
            docsSorted = docsSorted.sort(sortProp);

        long first = params.getFirstResultToShow();
        if (first < 0)
            first = 0;
        long number = params.getNumberOfResultsToShow();
        if (number < 0 || number > searchMan.config().getParameters().getPageSize().getMax())
            number = searchMan.config().getParameters().getPageSize().getDefaultValue();
        totalDocResults = docsSorted;
        window = docsSorted.window(first, number);

        originalHitsSearch = null; // don't use this to report totals, because we've filtered since then
        docResults = group.storedResults();
        totalTime = docGroupFuture.timer().time();
        return doResponse(ds, true,
                WebserviceOperations.getAnnotationsToWrite(blIndex(), params),
                WebserviceOperations.getMetadataToWrite(blIndex(), params), true);
    }

    private int doRegularDocs(DataStream ds) throws BlsException, InvalidQuery {
        SearchCacheEntry<DocResults> searchWindow = params.docsWindow().executeAsync();
        search = searchWindow;

        // Also determine the total number of hits
        SearchCacheEntry<DocResults> total = params.docs().executeAsync();

        try {
            window = searchWindow.get();
            totalDocResults = total.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }

        // If "waitfortotal=yes" was passed, block until all results have been fetched
        boolean waitForTotal = params.getWaitForTotal();
        if (waitForTotal)
            totalDocResults.size();

        docResults = totalDocResults;
        totalTime = total.threwException() ? 0 : total.timer().time();

        return doResponse(ds, false,
                WebserviceOperations.getAnnotationsToWrite(blIndex(), params),
                WebserviceOperations.getMetadataToWrite(blIndex(), params), waitForTotal);
    }

    private int doResponse(DataStream ds, boolean isViewGroup, Collection<Annotation> annotationsTolist, Collection<MetadataField> metadataFieldsToList, boolean waitForTotal) throws BlsException, InvalidQuery {
        BlackLabIndex blIndex = blIndex();

        boolean includeTokenCount = params.getIncludeTokenCount();
        long totalTokens = -1;
        if (includeTokenCount) {
            // Determine total number of tokens in result set
            totalTokens = totalDocResults.subcorpusSize().getTokens();
        }

        // Search is done; construct the results object

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();
        ResultsStats hitsStats, docsStats;
        hitsStats = originalHitsSearch == null ? null : originalHitsSearch.peek();
        docsStats = params.docsCount().executeAsync().peek();
        SearchTimings timings = new SearchTimings(search.timer().time(), totalTime);
        DataStreamUtil.summaryCommonFields(ds, params, indexMan, timings, null, window.windowStats());
        boolean countFailed = totalTime < 0;
        if (hitsStats == null)
            DataStreamUtil.numberOfResultsSummaryDocResults(ds, isViewGroup, docResults, countFailed, null);
        else
            DataStreamUtil.numberOfResultsSummaryTotalHits(ds, hitsStats, docsStats, waitForTotal, countFailed, null);
        if (includeTokenCount)
            ds.entry("tokensInMatchingDocuments", totalTokens);

        Map<String, String> docFields = WebserviceOperations.getDocFields(blIndex().metadata());
        Map<String, String> metaDisplayNames = WebserviceOperations.getMetaDisplayNames(blIndex);
        DataStreamUtil.metadataFieldInfo(ds, docFields, metaDisplayNames);

        ds.endMap().endEntry();

        // The hits and document info
        ds.startEntry("docs").startList();
        for (DocResult result : window) {
            // Find pid
            Document document = blIndex().luceneDoc(result.docId());
            String pid = WebserviceOperations.getDocumentPid(blIndex, result.identity().value(), document);
            ResultDocInfo docInfo = WebserviceOperations.getDocInfo(blIndex, document, metadataFieldsToList);

            ds.startItem("doc").startMap();

            // Combine all
            ds.entry("docPid", pid);
            long numHits = result.size();
            if (numHits > 0)
                ds.entry("numberOfHits", numHits);

            // Doc info (metadata, etc.)
            ds.startEntry("docInfo");
            DataStreamUtil.documentInfo(ds, docInfo);
            ds.endEntry();

            // Snippets
            Hits hits2 = result.storedResults().window(0, 5); // TODO: make num. snippets configurable
            if (hits2.hitsStats().processedAtLeast(1)) {
                ds.startEntry("snippets").startList();
                ContextSettings contextSettings = params.contextSettings();
                Concordances concordances = null;
                Kwics kwics = null;
                if (contextSettings.concType() == ConcordanceType.CONTENT_STORE)
                    concordances = hits2.concordances(contextSettings.size(), ConcordanceType.CONTENT_STORE);
                else
                    kwics = hits2.kwics(blIndex.defaultContextSize());
                for (Hit hit : hits2) {
                    // TODO: use RequestHandlerDocSnippet.getHitOrFragmentInfo()
                    ds.startItem("snippet").startMap();
                    if (contextSettings.concType() == ConcordanceType.CONTENT_STORE) {
                        // Add concordance from original XML
                        Concordance c = concordances.get(hit);
                        ds.startEntry("left").xmlFragment(c.left()).endEntry()
                                .startEntry("match").xmlFragment(c.match()).endEntry()
                                .startEntry("right").xmlFragment(c.right()).endEntry();
                    } else {
                        // Add KWIC info
                        Kwic c = kwics.get(hit);
                        ds.startEntry("left").contextList(c.annotations(), annotationsTolist, c.left()).endEntry()
                                .startEntry("match").contextList(c.annotations(), annotationsTolist, c.match()).endEntry()
                                .startEntry("right").contextList(c.annotations(), annotationsTolist, c.right()).endEntry();
                    }
                    ds.endMap().endItem();
                }
                ds.endList().endEntry();
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();
        if (params.hasFacets()) {
            // Now, group the docs according to the requested facets.
            ds.startEntry("facets");

            Map<DocProperty, DocGroups> counts = params.facets().execute().countsPerFacet();
            DataStreamUtil.facets(ds, WebserviceOperations.getFacetInfo(counts));
            ds.endEntry();
        }
        ds.endMap();
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
