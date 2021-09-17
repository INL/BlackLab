package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.DocPropertyMultiple;
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
    public int handle(DataStream ds) throws BlsException, InvalidQuery {

        // Make sure we have the hits search, so we can later determine totals.
        BlsCacheEntry<ResultCount> originalHitsSearch = null;
        if (searchParam.hasPattern()) {
            originalHitsSearch = (BlsCacheEntry<ResultCount>)searchParam.hitsCount().executeAsync();
        }
        // Get the window we're interested in
        DocResults docResults = searchParam.docs().execute();
        BlsCacheEntry<DocGroups> groupSearch = (BlsCacheEntry<DocGroups>)searchParam.docsGrouped().executeAsync();
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
        ResultCount docsStats = searchParam.docsCount().execute();

        // The list of groups found
        DocProperty metadataGroupProperties = null;
        DocResults subcorpus = null;
        CorpusSize subcorpusSize = null;
        boolean hasPattern = searchParam.hasPattern();
        if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
            metadataGroupProperties = groups.groupCriteria();
            subcorpus = searchParam.subcorpus().execute();
            subcorpusSize = subcorpus.subcorpusSize();
        }

        addSummaryCommonFields(ds, searchParam, groupSearch.timeUserWaitedMs(), 0, groups, ourWindow);
        if (totalHits == null)
            addNumberOfResultsSummaryDocResults(ds, false, docResults, false, subcorpusSize);
        else
            addNumberOfResultsSummaryTotalHits(ds, totalHits, docsStats, false, subcorpusSize);

        ds.endMap().endEntry();
        searchLogger.setResultsFound(groups.size());

        /* Gather group values per property:
         * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
         * contain the sub properties and values in the same order.
         */
        boolean isMultiValueGroup = groups.groupCriteria() instanceof DocPropertyMultiple;
        List<DocProperty> prop = isMultiValueGroup ? ((DocPropertyMultiple) groups.groupCriteria()).props() : Arrays.asList(groups.groupCriteria());

        ds.startEntry("docGroups").startList();
        int last = Math.min(first + number, groups.size());
        for (int i = first; i < last; ++i) {
            DocGroup group = groups.get(i);
            List<PropertyValue> valuesForGroup = isMultiValueGroup ? group.identity().values() : Arrays.asList(group.identity());

            ds.startItem("docgroup").startMap()
                    .entry("identity", group.identity().serialize())
                    .entry("identityDisplay", group.identity().toString())
                    .entry("size", group.size());

            // Write the raw values for this group
            ds.startEntry("properties").startList();
            for (int j = 0; j < prop.size(); ++j) {
                final DocProperty hp = prop.get(j);
                final PropertyValue pv = valuesForGroup.get(j);

                ds.startItem("property").startMap();
                ds.entry("name", hp.serialize());
                ds.entry("value", pv.toString());
                ds.endMap().endItem();
            }
            ds.endList().endEntry();

            if (RequestHandlerHitsGrouped.INCLUDE_RELATIVE_FREQ) {
                ds.entry("numberOfTokens", group.totalTokens());
                if (hasPattern) {
                    // Find size of corresponding subcorpus group
                    PropertyValue docPropValues = group.identity();
                    subcorpusSize = RequestHandlerHitsGrouped.findSubcorpusSize(searchParam, subcorpus.query(), metadataGroupProperties, docPropValues, true);
                    addSubcorpusSize(ds, subcorpusSize);
                }
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
