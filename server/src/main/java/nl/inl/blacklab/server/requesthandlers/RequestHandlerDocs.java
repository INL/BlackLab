package nl.inl.blacklab.server.requesthandlers;

import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.BlsCacheEntry;

/**
 * Request handler for the doc results.
 */
public class RequestHandlerDocs extends RequestHandler {
    
    public RequestHandlerDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    BlsCacheEntry<?> search = null;
    BlsCacheEntry<ResultCount> originalHitsSearch;
    DocResults totalDocResults;
    DocResults window;
    private DocResults docResults;
    private long totalTime;
    
    @Override
    public int handle(DataStream ds) throws BlsException {
        // Do we want to view a single group after grouping?
        String groupBy = searchParam.getString("group");
        if (groupBy == null)
            groupBy = "";
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup == null)
            viewGroup = "";
        int response = 0;
        
        // Make sure we have the hits search, so we can later determine totals.
        originalHitsSearch = null;
        if (searchParam.hasPattern()) {
            originalHitsSearch = searchMan.searchNonBlocking(user, searchParam.hitsCount());
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

    private int doViewGroup(DataStream ds, String viewGroup) throws BlsException {
        // TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)
    
        BlsCacheEntry<DocGroups> docGroupFuture;
        // Yes. Group, then show hits from the specified group
        search = docGroupFuture = searchMan.searchNonBlocking(user, searchParam.docsGrouped());
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
        String sortBy = searchParam.getString("sort");
        DocProperty sortProp = sortBy != null && sortBy.length() > 0 ? DocProperty.deserialize(blIndex(), sortBy) : null;
        DocResults docsSorted = group.storedResults();
        if (sortProp != null)
            docsSorted = docsSorted.sort(sortProp);
    
        int first = searchParam.getInteger("first");
        if (first < 0)
            first = 0;
        int number = searchParam.getInteger("number");
        if (number < 0 || number > searchMan.config().getParameters().getPageSize().getMax())
            number = searchMan.config().getParameters().getPageSize().getDefaultValue();
        totalDocResults = docsSorted;
        window = docsSorted.window(first, number);
        
        originalHitsSearch = null; // don't use this to report totals, because we've filtered since then
        docResults = group.storedResults();
        totalTime = 0; // TODO searchGrouped.userWaitTime();
        return doResponse(ds, true);
    }

    private int doRegularDocs(DataStream ds) throws BlsException {
        BlsCacheEntry<DocResults> searchWindow = searchMan.searchNonBlocking(user, searchParam.docsWindow());
        search = searchWindow;
    
        // Also determine the total number of hits
        BlsCacheEntry<DocResults> total = searchMan.searchNonBlocking(user, searchParam.docs());
        
        try {
            window = searchWindow.get();
            totalDocResults = total.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }
        
        // If "waitfortotal=yes" was passed, block until all results have been fetched
        boolean block = searchParam.getBoolean("waitfortotal");
        if (block)
            totalDocResults.size(); // fetch all
        
        docResults = totalDocResults;
        totalTime = total.threwException() ? -1 : total.timeUserWaited();
        
        return doResponse(ds, false);
}

    private int doResponse(DataStream ds, boolean isViewGroup) throws BlsException {
        BlackLabIndex blIndex = blIndex();

        boolean includeTokenCount = searchParam.getBoolean("includetokencount");
        long totalTokens = -1;
        if (includeTokenCount) {
            // Determine total number of tokens in result set
            totalTokens = totalDocResults.subcorpusSize().getTokens();
        }

        // Search is done; construct the results object

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();
        ResultCount totalHits;
        try {
            totalHits = originalHitsSearch == null ? null : originalHitsSearch.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }
        ResultCount docsStats = searchMan.search(user, searchParam.docsCount());
        addSummaryCommonFields(ds, searchParam, search.timeUserWaited(), totalTime, null, window.windowStats());
        boolean countFailed = totalTime < 0;
        if (totalHits == null)
            addNumberOfResultsSummaryDocResults(ds, isViewGroup, docResults, countFailed, null);
        else
            addNumberOfResultsSummaryTotalHits(ds, totalHits, docsStats, countFailed, null);
        if (includeTokenCount)
            ds.entry("tokensInMatchingDocuments", totalTokens);
        ds.startEntry("docFields");
        RequestHandler.dataStreamDocFields(ds, blIndex.metadata());
        ds.endEntry();
        ds.endMap().endEntry();
        
        searchLogger.setResultsFound(docsStats.processedSoFar());

        // The hits and document info
        ds.startEntry("docs").startList();
        for (DocResult result : window) {
            ds.startItem("doc").startMap();

            // Find pid
            Document document = result.identity().luceneDoc();
            String pid = getDocumentPid(blIndex, result.identity().id(), document);

            // Combine all
            ds.entry("docPid", pid);
            int numHits = result.size();
            if (numHits > 0)
                ds.entry("numberOfHits", numHits);

            // Doc info (metadata, etc.)
            ds.startEntry("docInfo");
            dataStreamDocumentInfo(ds, blIndex, document);
            ds.endEntry();

            // Snippets
            Hits hits2 = result.storedResults().window(0, 5); // TODO: make num. snippets configurable
            if (hits2.hitsStats().processedAtLeast(1)) {
                ds.startEntry("snippets").startList();
                ContextSettings contextSettings = searchParam.getContextSettings();
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
                        ds.startEntry("left").plain(c.left()).endEntry()
                                .startEntry("match").plain(c.match()).endEntry()
                                .startEntry("right").plain(c.right()).endEntry();
                    } else {
                        // Add KWIC info
                        Kwic c = kwics.get(hit);
                        ds.startEntry("left").contextList(c.annotations(), c.left()).endEntry()
                                .startEntry("match").contextList(c.annotations(), c.match()).endEntry()
                                .startEntry("right").contextList(c.annotations(), c.right()).endEntry();
                    }
                    ds.endMap().endItem();
                }
                ds.endList().endEntry();
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();
        if (searchParam.hasFacets()) {
            // Now, group the docs according to the requested facets.
            ds.startEntry("facets");
            dataStreamFacets(ds, totalDocResults, searchParam.facets());
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
