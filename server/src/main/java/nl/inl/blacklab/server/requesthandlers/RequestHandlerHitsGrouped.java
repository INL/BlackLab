package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.tuple.Pair;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.ResultHitGroup;
import nl.inl.blacklab.server.lib.requests.ResultHitsGrouped;
import nl.inl.blacklab.server.lib.requests.WebserviceOperations;

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
        ResultHitsGrouped hitsGrouped = WebserviceOperations.hitsGrouped(params, searchMan, indexMan);
        dstreamHitsGroupedResponse(ds, hitsGrouped);
        return HTTP_OK;
    }

    private static void dstreamHitsGroupedResponse(DataStream ds, ResultHitsGrouped hitsGrouped) {
        Index.IndexStatus indexStatus = hitsGrouped.getIndexStatus();
        SearchCreator params = hitsGrouped.getParams();

        ds.startMap();

        // Summary
        ds.startEntry("summary").startMap();
        {
            DStream.summaryCommonFields(ds, params, indexStatus, hitsGrouped.getTimings(),
                    hitsGrouped.getGroups(), hitsGrouped.getWindow());
            DStream.numberOfResultsSummaryTotalHits(ds, hitsGrouped.getHitsStats(), hitsGrouped.getDocsStats(),
                    true, false, hitsGrouped.getSubcorpusSize());
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
                    DStream.listOfHits(ds, params, hitsInGroup, groupInfo.getConcordanceContext(),
                            groupInfo.getDocIdToPid());
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
