package nl.inl.blacklab.server.requesthandlers;

import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.ResultCount;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.BlsCacheEntry;

/**
 * Request handler for grouped doc results.
 */
public class RequestHandlerDocsGrouped extends RequestHandler {
    public RequestHandlerDocsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {

        // Make sure we have the hits search, so we can later determine totals.
        BlsCacheEntry<ResultCount> originalHitsSearch = null;
        if (searchParam.hasPattern()) {
            originalHitsSearch = searchMan.searchNonBlocking(user, searchParam.hitsCount());
        }
        // Get the window we're interested in
        DocResults docResults = searchMan.search(user, searchParam.docs());
        BlsCacheEntry<DocGroups> groupSearch = searchMan.searchNonBlocking(user, searchParam.docsGrouped());
        DocGroups groups;
        try {
            groups = groupSearch.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }

        // Search is done; construct the results object

        int first = searchParam.getInteger("first");
        if (first < 0)
            first = 0;
        int number = searchParam.getInteger("number");
        if (number < 0 || number > searchMan.config().getParameters().getPageSize().getMax())
            number = searchMan.config().getParameters().getPageSize().getDefaultValue();
        int numberOfGroupsInWindow = 0;
        numberOfGroupsInWindow = number;
        if (first + number > groups.size())
            numberOfGroupsInWindow = groups.size() - first;

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();
        WindowStats ourWindow = new WindowStats(first + number < groups.size(), first, number, numberOfGroupsInWindow);
        ResultCount totalHits;
        try {
            totalHits = originalHitsSearch == null ? null : originalHitsSearch.get();
        } catch (InterruptedException | ExecutionException e) {
            throw RequestHandler.translateSearchException(e);
        }
        ResultCount docsStats = searchMan.search(user, searchParam.docsCount());
        
        // The list of groups found
        DocProperty metadataGroupProperties = null;
        CorpusSize subcorpusSize = null;
        if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
            logger.debug("## Init relative frequencies: get doc props");
            metadataGroupProperties = groups.groupCriteria();
            logger.debug("## Init relative frequencies: determine subcorpus");
            DocResults subcorpus = searchMan.search(user, searchParam.subcorpus());
            if (metadataGroupProperties == null) {
                // We're not grouping on metadata. We only need to know the total subcorpus size.
                logger.debug("## NOT grouping on metadata, count tokens in total subcorpus");
                subcorpusSize = subcorpus.subcorpusSize(false);
                logger.debug("## (docs in total subcorpus: " + subcorpusSize.getDocuments() + ")");
            }
            logger.debug("## Done init relative frequencies");
        }
        
        addSummaryCommonFields(ds, searchParam, groupSearch.timeUserWaited(), 0, groups, ourWindow);
        if (totalHits == null)
            addNumberOfResultsSummaryDocResults(ds, false, docResults, false);
        else
            addNumberOfResultsSummaryTotalHits(ds, totalHits, docsStats, false, null);
        
        ds.endMap().endEntry();

        searchLogger.setResultsFound(groups.size());
        
        int i = 0;
        ds.startEntry("docGroups").startList();
        for (DocGroup group : groups) {
            if (i >= first && i < first + number) {
                
                if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ && metadataGroupProperties != null) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = group.identity();
                    //DocGroup groupSubcorpus = subcorpusGrouped.get(docPropValues);
                    //tokensInSubcorpus = groupSubcorpus.storedResults().tokensInMatchingDocs();
                    subcorpusSize = RequestHandlerHitsGrouped.findSubcorpusSize(searchParam, metadataGroupProperties, docPropValues, false);
                    logger.debug("## docs in subcorpus group: " + subcorpusSize.getDocuments());
                }
                
                ds.startItem("docgroup").startMap()
                        .entry("identity", group.identity().serialize())
                        .entry("identityDisplay", group.identity().toString())
                        .entry("size", group.size());
                if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
                    if (subcorpusSize != null) {
                        addSubcorpusSize(ds, subcorpusSize);
                    }
                }
                ds.endMap().endItem();
            }
            i++;
        }
        ds.endList().endEntry();

        ds.endMap();

        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
