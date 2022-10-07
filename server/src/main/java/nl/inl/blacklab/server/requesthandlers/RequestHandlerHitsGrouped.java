package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.tuple.Pair;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultHitGroup;
import nl.inl.blacklab.server.lib.results.ResultHitsGrouped;
import nl.inl.blacklab.server.lib.results.ResultListOfHits;
import nl.inl.blacklab.server.lib.results.ResultSummaryCommonFields;
import nl.inl.blacklab.server.lib.results.ResultSummaryNumHits;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Request handler for grouped hit results.
 */
public class RequestHandlerHitsGrouped extends RequestHandler {

    public RequestHandlerHitsGrouped(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        ResultHitsGrouped hitsGrouped = WebserviceOperations.hitsGrouped(params);
        dstreamHitsGroupedResponse(ds, hitsGrouped);
        return HTTP_OK;
    }

    private static void dstreamHitsGroupedResponse(DataStream ds, ResultHitsGrouped hitsGrouped) {
        SearchCreator params = hitsGrouped.getParams();
        ResultSummaryCommonFields summaryFields = hitsGrouped.getSummaryFields();
        ResultSummaryNumHits result = hitsGrouped.getSummaryNumHits();

        ds.startMap();

        // Summary
        ds.startEntry("summary").startMap();
        {
            DStream.summaryCommonFields(ds, summaryFields);
            DStream.summaryNumHits(ds, result);
        }
        ds.endMap().endEntry();

        ds.startEntry("hitGroups").startList();

        List<ResultHitGroup> groupInfos = hitsGrouped.getGroupInfos();
        for (ResultHitGroup groupInfo: groupInfos) {
            ds.startItem("hitgroup").startMap();
            {
                ds
                        .entry("identity", groupInfo.getIdentity())
                        .entry("identityDisplay", groupInfo.getIdentityDisplay())
                        .entry("size", groupInfo.getSize());

                ds.startEntry("properties").startList();
                for (Pair<String, String> p: groupInfo.getProperties()) {
                    ds.startItem("property").startMap();
                    {
                        ds.entry("name", p.getKey());
                        ds.entry("value", p.getValue());
                    }
                    ds.endMap().endItem();
                }
                ds.endList().endEntry();

                ds.entry("numberOfDocs", groupInfo.getNumberOfDocsInGroup());
                if (hitsGrouped.getMetadataGroupProperties() != null) {
                    DStream.subcorpusSize(ds, groupInfo.getSubcorpusSize());
                }

                if (params.includeGroupContents()) {
                    Hits hitsInGroup = groupInfo.getGroup().storedResults();
                    ResultListOfHits listOfHits = WebserviceOperations.listOfHits(params, hitsInGroup,
                            groupInfo.getConcordanceContext(),
                            groupInfo.getDocIdToPid());
                    DStream.listOfHits(ds, listOfHits);
                }
            }
            ds.endMap().endItem();
        }
        ds.endList().endEntry();

        if (params.includeGroupContents()) {
            DStream.documentInfos(ds, hitsGrouped.getDocInfos());
        }
        ds.endMap();
    }

}
