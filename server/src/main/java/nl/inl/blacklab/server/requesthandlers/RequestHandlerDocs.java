package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyAnnotatedFieldLength;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocOrHitGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.DocResultsWindow;
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
import nl.inl.blacklab.server.jobs.User;

/**
 * Request handler for the doc results.
 */
public class RequestHandlerDocs extends RequestHandler {
    public RequestHandlerDocs(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        // Do we want to view a single group after grouping?
        String groupBy = searchParam.getString("group");
        if (groupBy == null)
            groupBy = "";
        String viewGroup = searchParam.getString("viewgroup");
        if (viewGroup == null)
            viewGroup = "";
        Job search = null;
        JobDocsGrouped searchGrouped = null;
        JobDocsWindow searchWindow = null;
        JobDocsTotal total = null;
        try {
            DocResultsWindow window;
            DocGroup group = null;
            boolean block = isBlockingOperation();
            if (groupBy.length() > 0 && viewGroup.length() > 0) {

                // TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)

                // Yes. Group, then show hits from the specified group
                searchGrouped = (JobDocsGrouped) searchMan.search(user, searchParam.docsGrouped(), block);
                search = searchGrouped;
                search.incrRef();

                // If search is not done yet, indicate this to the user
                if (!search.finished()) {
                    return Response.busy(ds, servlet);
                }

                // Search is done; construct the results object
                DocGroups groups = searchGrouped.getGroups();

                HitPropValue viewGroupVal = null;
                viewGroupVal = HitPropValue.deserialize(groups.getOriginalDocResults().originalHits(), viewGroup);
                if (viewGroupVal == null)
                    return Response.badRequest(ds, "ERROR_IN_GROUP_VALUE",
                            "Parameter 'viewgroup' has an illegal value: " + viewGroup);

                group = groups.getGroup(viewGroupVal);
                if (group == null)
                    return Response.badRequest(ds, "GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
                String sortBy = searchParam.getString("sort");
                DocProperty sortProp = sortBy != null && sortBy.length() > 0 ? DocProperty.deserialize(sortBy) : null;
                DocResults docsSorted;
                if (sortProp != null) {
                    docsSorted = group.getResults();
                    docsSorted.sort(sortProp, false);
                } else
                    docsSorted = group.getResults();

                int first = searchParam.getInteger("first");
                if (first < 0)
                    first = 0;
                int number = searchParam.getInteger("number");
                if (number < 0 || number > searchMan.config().maxPageSize())
                    number = searchMan.config().defaultPageSize();
                window = docsSorted.window(first, number);

            } else {
                // Regular set of docs (no grouping first)

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
            }

            BlackLabIndex blIndex = search.blIndex();

            boolean includeTokenCount = searchParam.getBoolean("includetokencount");
            int totalTokens = -1;
            if (includeTokenCount) {
                // Determine total number of tokens in result set
                //TODO: use background job?
                String fieldName = blIndex.mainAnnotatedField().name();
                DocProperty propTokens = new DocPropertyAnnotatedFieldLength(fieldName);
                totalTokens = window.getOriginalDocs().intSum(propTokens);
            }

            // Search is done; construct the results object

            ds.startMap();

            // The summary
            ds.startEntry("summary").startMap();
            DocResults docResults = group == null ? total.getDocResults() : group.getResults();
            double totalTime = group == null ? (total.threwException() ? -1 : total.userWaitTime())
                    : searchGrouped.userWaitTime();
            addSummaryCommonFields(ds, searchParam, search.userWaitTime(), totalTime, (Hits) null, (Hits) null,
                    group != null, docResults, (DocOrHitGroups) null, window);
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
                Document document = result.getDocument();
                String pid = getDocumentPid(blIndex, result.getDocId(), document);

                // Combine all
                ds.entry("docPid", pid);
                int numHits = result.getNumberOfHits();
                if (numHits > 0)
                    ds.entry("numberOfHits", numHits);

                // Doc info (metadata, etc.)
                ds.startEntry("docInfo");
                dataStreamDocumentInfo(ds, blIndex, document);
                ds.endEntry();

                // Snippets
                Hits hits2 = result.getHits(5); // TODO: make num. snippets configurable
                if (hits2.hitsProcessedAtLeast(1)) {
                    ds.startEntry("snippets").startList();
                    ContextSettings contextSettings = searchParam.getContextSettings();
                    Concordances concordances = null;
                    Kwics kwics = null;
                    if (contextSettings.concType() == ConcordanceType.CONTENT_STORE)
                        concordances = hits2.concordances(contextSettings.size(), ConcordanceType.CONTENT_STORE);
                    else
                        kwics = hits2.kwics(-1);
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
                            ds.startEntry("left").contextList(c.getProperties(), c.getLeft()).endEntry()
                                    .startEntry("match").contextList(c.getProperties(), c.getMatch()).endEntry()
                                    .startEntry("right").contextList(c.getProperties(), c.getRight()).endEntry();
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
                dataStreamFacets(ds, window.getOriginalDocs(), searchParam.facets());
                ds.endEntry();
            }
            ds.endMap();
            return HTTP_OK;
        } finally {
            if (search != null)
                search.decrRef();
            if (searchWindow != null)
                searchWindow.decrRef();
            if (searchGrouped != null)
                searchGrouped.decrRef();
            if (total != null)
                total.decrRef();
        }
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
