package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.BlsIndexOpenException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultIndexProgress;
import nl.inl.blacklab.server.lib.results.ResultUserInfo;
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
        Collection<Index> indices = indexMan.getAllAvailableIndices(user.getUserId());
        ResultUserInfo userInfo = WebserviceOperations.userInfo(user.isLoggedIn(), user.getUserId(),
                indexMan.canCreateIndex(user));

        ds.startMap()
                .entry("blacklabBuildTime", BlackLab.buildTime())
                .entry("blacklabVersion", BlackLab.version());

        ds.startEntry("indices").startMap();

        for (Index index : indices) {
            try {
                IndexMetadata indexMetadata = index.getIndexMetadata();
                String displayName = indexMetadata.custom().get("displayName", "");
                String description = indexMetadata.custom().get("description", "");
                ResultIndexProgress progress;
                synchronized (index) {
                    progress = WebserviceOperations.resultIndexProgress(indexMetadata, index.getIndexerListener(),
                            index.getStatus());
                }

                ds.startAttrEntry("index", "name", index.getId());
                {
                    ds.startMap();
                    {
                        ds.entry("displayName", displayName);
                        ds.entry("description", description);
                        ds.entry("status", index.getStatus());
                        DStream.indexProgress(ds, progress);
                        ds.entry("timeModified", indexMetadata.timeModified());
                        ds.entry("tokenCount", indexMetadata.tokenCount());
                    }
                    ds.endMap();
                }
                ds.endAttrEntry();

            } catch (BlsIndexOpenException e) {
                // Cannot open this index; log and skip it.
                logger.warn("Could not open index " + index.getId() + ": " + e.getMessage());
            }
        }
        ds.endMap().endEntry();

        DStream.userInfo(ds, userInfo);

        if (debugMode) {
            ds.startEntry("cacheStatus");
            ds.value(searchMan.getBlackLabCache().getStatus());
            ds.endEntry();
        }
        ds.endMap();

        return HTTP_OK;
    }

}
