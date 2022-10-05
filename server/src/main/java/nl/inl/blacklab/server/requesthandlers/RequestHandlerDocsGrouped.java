package nl.inl.blacklab.server.requesthandlers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumDocs;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumHits;
import nl.inl.blacklab.server.lib.results.ResultSummaryCommonFields;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Request handler for grouped doc results.
 */
public class RequestHandlerDocsGrouped extends RequestHandler {
    public RequestHandlerDocsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    static class ResultDocsGrouped {

        private SearchCreator params;

        private DocGroups groups;

        private ResultsStats hitsStats;

        private ResultsStats docsStats;

        private WindowStats ourWindow;

    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {

        // Make sure we have the hits search, so we can later determine totals.
        SearchCacheEntry<ResultsStats> originalHitsSearch = null;
        if (params.hasPattern()) {
            originalHitsSearch = params.hitsSample().hitCount().executeAsync();
        }
        // Get the window we're interested in
        DocResults docResults = params.docs().execute();
        SearchCacheEntry<DocGroups> groupSearch = params.docsGrouped().executeAsync();
        DocGroups groups;
        try {
            groups = groupSearch.get();
        } catch (InterruptedException | ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        // Search is done; construct the results object

        long first = params.getFirstResultToShow();
        if (first < 0)
            first = 0;
        long number = params.getNumberOfResultsToShow();
        if (number < 0 || number > searchMan.config().getParameters().getPageSize().getMax())
            number = searchMan.config().getParameters().getPageSize().getDefaultValue();
        long numberOfGroupsInWindow = 0;
        numberOfGroupsInWindow = number;
        if (first + number > groups.size())
            numberOfGroupsInWindow = groups.size() - first;
        WindowStats ourWindow = new WindowStats(first + number < groups.size(), first, number, numberOfGroupsInWindow);

        ResultsStats hitsStats, docsStats;
        hitsStats = originalHitsSearch == null ? null : originalHitsSearch.peek();
        docsStats = params.docsCount().executeAsync().peek();

        // The list of groups found
        boolean hasPattern = params.hasPattern();
        DocProperty metadataGroupProperties = groups.groupCriteria();
        DocResults subcorpus = params.subcorpus().execute();
        CorpusSize subcorpusSize = subcorpus.subcorpusSize();

        SearchTimings timings = new SearchTimings(groupSearch.timer().time(), 0);
        Index.IndexStatus indexStatus = indexMan.getIndex(params.getIndexName()).getStatus();
        ResultSummaryCommonFields summaryFields = WebserviceOperations.summaryCommonFields(params,
                indexStatus, timings, groups, ourWindow);

        ResultSummaryNumDocs numResultDocs = null;
        ResultSummaryNumHits numResultHits = null;
        if (hitsStats == null) {
            numResultDocs = WebserviceOperations.numResultsSummaryDocs(false, docResults, false,
                    subcorpusSize);
        } else {
            numResultHits = WebserviceOperations.numResultsSummaryHits(
                    hitsStats, docsStats, true, false, subcorpusSize);
        }

        List<CorpusSize> corpusSizes = new ArrayList<>();
        if (hasPattern) {
            for (long i = ourWindow.first(); i <= ourWindow.last(); ++i) {
                DocGroup group = groups.get(i);
                    // Find size of corresponding subcorpus group
                    CorpusSize size = WebserviceOperations.findSubcorpusSize(params, subcorpus.query(),
                            metadataGroupProperties, group.identity());
                    corpusSizes.add(size);
            }
        }

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();

        DStream.summaryCommonFields(ds, summaryFields);

        if (numResultDocs != null) {
            DStream.summaryNumDocs(ds, numResultDocs);
        } else {
            DStream.summaryNumHits(ds, numResultHits);
        }

        ds.endMap().endEntry();

        ds.startEntry("docGroups").startList();
        Iterator<CorpusSize> it = corpusSizes.iterator();
        /* Gather group values per property:
         * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
         * contain the sub properties and values in the same order.
         */
        List<DocProperty> prop = groups.groupCriteria().propsList();
        for (long i = ourWindow.first(); i <= ourWindow.last(); ++i) {
            DocGroup group = groups.get(i);

            ds.startItem("docgroup").startMap()
                    .entry("identity", group.identity().serialize())
                    .entry("identityDisplay", group.identity().toString())
                    .entry("size", group.size());

            // Write the raw values for this group
            ds.startEntry("properties").startList();
            List<PropertyValue> valuesForGroup = group.identity().valuesList();
            for (int j = 0; j < prop.size(); ++j) {
                ds.startItem("property").startMap();
                ds.entry("name", prop.get(j).serialize());
                ds.entry("value", valuesForGroup.get(j).toString());
                ds.endMap().endItem();
            }
            ds.endList().endEntry();

            ds.entry("numberOfTokens", group.totalTokens());
            if (hasPattern) {
                DStream.subcorpusSize(ds, it.next());
            }
            ds.endMap().endItem();
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
