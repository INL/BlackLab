package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyAnnotatedFieldLength;
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
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.Job;
import nl.inl.blacklab.server.jobs.JobDocsGrouped;
import nl.inl.blacklab.server.jobs.JobDocsTotal;
import nl.inl.blacklab.server.jobs.JobDocsWindow;
import nl.inl.blacklab.server.jobs.JobHits;
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for the doc results.
 */
public class RequestHandlerDocs extends RequestHandler {
    
    public RequestHandlerDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    Job search;
    JobHits originalHitsSearch;
    DocResults totalDocResults;
    DocResults window;
    private DocResults docResults;
    private double totalTime;
    
    @Override
    public int handle(DataStream ds) throws BlsException {
        // Do we want to view a single group after grouping?
        String groupBy = searchParam.getString("group");
        if (groupBy == null)
            groupBy = "";
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup == null)
            viewGroup = "";
        search = null;
        try {
            boolean block = isBlockingOperation();
            int response = 0;
            
            // Make sure we have the hits search, so we can later determine totals.
            originalHitsSearch = null;
            if (searchParam.hasPattern()) {
                originalHitsSearch = (JobHits)searchMan.search(user, searchParam.hits(), false);
            }
            
            if (groupBy.length() > 0 && viewGroup.length() > 0) {
                
                // View a single group in a grouped docs resultset
                response = doViewGroup(ds, block, viewGroup);
                
            } else {
                // Regular set of docs (no grouping first)
                response = doRegularDocs(ds, block);
            }
            return response;
        } finally {
            if (search != null)
                search.decrRef();
            if (originalHitsSearch != null)
                originalHitsSearch.decrRef();
        }
    }

    private int doViewGroup(DataStream ds, boolean block, String viewGroup) throws BlsException {
        // TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)
    
        // Yes. Group, then show hits from the specified group
        JobDocsGrouped searchGrouped = null;
        try {
            searchGrouped = (JobDocsGrouped) searchMan.search(user, searchParam.docsGrouped(), block);
            search = searchGrouped;
            search.incrRef();
        
            // If search is not done yet, indicate this to the user
            if (!search.finished()) {
                return Response.busy(ds, servlet);
            }
        
            // Search is done; construct the results object
            DocGroups groups = searchGrouped.getGroups();
        
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
            DocProperty sortProp = sortBy != null && sortBy.length() > 0 ? DocProperty.deserialize(sortBy) : null;
            DocResults docsSorted = group.getStoredResults();
            if (sortProp != null)
                docsSorted = docsSorted.sortedBy(sortProp);
        
            int first = searchParam.getInteger("first");
            if (first < 0)
                first = 0;
            int number = searchParam.getInteger("number");
            if (number < 0 || number > searchMan.config().maxPageSize())
                number = searchMan.config().defaultPageSize();
            totalDocResults = docsSorted;
            window = docsSorted.window(first, number);
            
            docResults = group.getStoredResults();
            totalTime = searchGrouped.userWaitTime();
            return doResponse(ds, true);
        } finally {
            if (searchGrouped != null)
                searchGrouped.decrRef();
        }
    }

    private int doRegularDocs(DataStream ds, boolean block) throws BlsException {
        JobDocsWindow searchWindow = null;
        JobDocsTotal total = null;
        try {
            searchWindow = (JobDocsWindow) searchMan.search(user, searchParam.docsWindow(), block);
            search = searchWindow;
            search.incrRef();
        
            // Also determine the total number of hits
            // (usually nonblocking, unless "waitfortotal=yes" was passed)
            total = (JobDocsTotal) searchMan.search(user, searchParam.docsTotal(),
                    searchParam.getBoolean("waitfortotal"));
            
            // If search is not done yet, indicate this to the user
            if (!search.finished()) {
                return Response.busy(ds, servlet);
            }
        
            window = searchWindow.getDocResults();
        
            totalDocResults = total.getDocResults();
            
            docResults = total.getDocResults();
            totalTime = total.threwException() ? -1 : total.userWaitTime();
            
            return doResponse(ds, false);
        } finally {
            if (total != null)
                total.decrRef();
            if (searchWindow != null)
                searchWindow.decrRef();
        }
    }

    private int doResponse(DataStream ds, boolean isViewGroup) throws BlsException {
        BlackLabIndex blIndex = search.blIndex();

        boolean includeTokenCount = searchParam.getBoolean("includetokencount");
        int totalTokens = -1;
        if (includeTokenCount) {
            // Determine total number of tokens in result set
            //TODO: use background job?
            String fieldName = blIndex.mainAnnotatedField().name();
            DocProperty propTokens = new DocPropertyAnnotatedFieldLength(fieldName);
            totalTokens = totalDocResults.intSum(propTokens);
        }

        // Search is done; construct the results object

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();
        Hits totalHits = originalHitsSearch == null ? null : originalHitsSearch.getHits(); //docResults.originalHits();
        addSummaryCommonFields(ds, searchParam, search.userWaitTime(), totalTime, null, window.windowStats());
        boolean countFailed = totalTime < 0;
        if (totalHits == null)
            addNumberOfResultsSummaryDocResults(ds, isViewGroup, docResults, countFailed);
        else
            addNumberOfResultsSummaryTotalHits(ds, totalHits, countFailed);
        if (includeTokenCount)
            ds.entry("tokensInMatchingDocuments", totalTokens);
        ds.startEntry("docFields");
        RequestHandler.dataStreamDocFields(ds, blIndex.metadata());
        ds.endEntry();
        ds.endMap().endEntry();

        // The hits and document info
        ds.startEntry("docs").startList();
        for (DocResult result : window) {
            ds.startItem("doc").startMap();

            // Find pid
            Document document = result.getIdentity().getValue().luceneDoc();
            String pid = getDocumentPid(blIndex, result.getIdentity().getValue().id(), document);

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
            Hits hits2 = result.getStoredResults().window(0, 5); // TODO: make num. snippets configurable
            if (hits2.hitsProcessedAtLeast(1)) {
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
