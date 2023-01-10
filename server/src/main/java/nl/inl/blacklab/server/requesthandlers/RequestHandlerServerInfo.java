package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.ResultIndexStatus;
import nl.inl.blacklab.server.lib.results.ResultServerInfo;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Get information about this BlackLab server.
 */
public class RequestHandlerServerInfo extends RequestHandler {

    public RequestHandlerServerInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // You can create/delete indices, don't cache the list
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        ResultServerInfo result = WebserviceOperations.serverInfo(params, debugMode);
        dstreamServerInfo(ds, result);
        return HTTP_OK;
    }

    private void dstreamServerInfo(DataStream ds, ResultServerInfo result) {
        ds.startMap()
                .entry("blacklabBuildTime", BlackLab.buildTime())
                .entry("blacklabVersion", BlackLab.version());

        ds.startEntry("indices").startMap();
        for (ResultIndexStatus indexStatus: result.getIndexStatuses()) {
            dstreamIndexInfo(ds, indexStatus);
        }
        ds.endMap().endEntry();

        DStream.userInfo(ds, result.getUserInfo());

        if (result.isDebugMode()) {
            ds.startEntry("cacheStatus");
            ds.value(result.getParams().getSearchManager().getBlackLabCache().getStatus());
            ds.endEntry();
        }
        ds.endMap();
    }

    private void dstreamIndexInfo(DataStream ds, ResultIndexStatus progress) {
        Index index = progress.getIndex();
        IndexMetadata indexMetadata = progress.getMetadata();
        ds.startAttrEntry("index", "name", index.getId());
        {
            ds.startMap();
            {
                ds.entry("displayName", indexMetadata.custom().get("displayName", ""));
                ds.entry("description", indexMetadata.custom().get("description", ""));
                ds.entry("status", index.getStatus());
                DStream.indexProgress(ds, progress);
                ds.entry("timeModified", indexMetadata.timeModified());
                ds.entry("tokenCount", indexMetadata.tokenCount());
            }
            ds.endMap();
        }
        ds.endAttrEntry();
    }

}
