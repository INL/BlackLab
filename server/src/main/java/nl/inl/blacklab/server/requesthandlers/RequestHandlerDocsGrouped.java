package nl.inl.blacklab.server.requesthandlers;

import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultDocsGrouped;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

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
        ResultDocsGrouped result = WebserviceOperations.docsGrouped(params);
        dstreamDocsGroupedResponse(ds, result);
        return HTTP_OK;
    }

    private void dstreamDocsGroupedResponse(DataStream ds, ResultDocsGrouped result) {
        DocGroups groups = result.getGroups();
        WindowStats ourWindow = result.getOurWindow();

        ds.startMap();

        // The summary
        ds.startEntry("summary").startMap();

        DStream.summaryCommonFields(ds, result.getSummaryFields());

        if (result.getNumResultDocs() != null) {
            DStream.summaryNumDocs(ds, result.getNumResultDocs());
        } else {
            DStream.summaryNumHits(ds, result.getNumResultHits());
        }

        ds.endMap().endEntry();

        ds.startEntry("docGroups").startList();
        Iterator<CorpusSize> it = result.getCorpusSizes().iterator();
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
            if (result.getParams().hasPattern()) {
                DStream.subcorpusSize(ds, it.next());
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();

        ds.endMap();
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
