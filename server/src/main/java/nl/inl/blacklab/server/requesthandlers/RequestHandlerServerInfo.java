package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.IndexTooOld;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.Index.IndexStatus;
import nl.inl.blacklab.server.jobs.User;

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

        ds.startMap()
                .entry("blacklabBuildTime", BlackLabIndexImpl.blackLabBuildTime())
                .entry("blacklabVersion", BlackLabIndexImpl.blackLabVersion());

        ds.startEntry("indices").startMap();

        for (Index index : indices) {
            ds.startAttrEntry("index", "name", index.getId());
            ds.startMap();

            try {
                synchronized (index) {
                    IndexMetadata indexMetadata = index.getIndexMetadata();
                    IndexStatus status = index.getStatus();

                    ds.entry("displayName", indexMetadata.displayName());
                    ds.entry("description", indexMetadata.description());
                    ds.entry("status", status);

                    if (status.equals(IndexStatus.INDEXING)) {
                        IndexListener indexProgress = index.getIndexerListener();
                        synchronized (indexProgress) {
                            ds.startEntry("indexProgress").startMap()
                                    .entry("filesProcessed", indexProgress.getFilesProcessed())
                                    .entry("docsDone", indexProgress.getDocsDone())
                                    .entry("tokensProcessed", indexProgress.getTokensProcessed())
                                    .endMap().endEntry();
                        }
                    }

                    String formatIdentifier = indexMetadata.documentFormat();
                    if (formatIdentifier != null && formatIdentifier.length() > 0)
                        ds.entry("documentFormat", formatIdentifier);
                    ds.entry("timeModified", indexMetadata.timeModified());
                    if (indexMetadata.tokenCount() > 0)
                        ds.entry("tokenCount", indexMetadata.tokenCount());
                }
            } catch (IndexTooOld e) {
                // Cannot open this index; log and skip it.
                logger.warn("Could not open index " + index.getId() + ": " + e.getMessage());
            }

            ds.endMap();
            ds.endAttrEntry();
        }
        ds.endMap().endEntry();

        ds.startEntry("user").startMap();
        ds.entry("loggedIn", user.isLoggedIn());
        if (user.isLoggedIn())
            ds.entry("id", user.getUserId());
        boolean canCreateIndex = user.isLoggedIn() ? indexMan.canCreateIndex(user) : false;
        ds.entry("canCreateIndex", canCreateIndex);
        ds.endMap().endEntry();

        ds.entry("helpPageUrl", servlet.getServletContext().getContextPath() + "/help");
        if (debugMode) {
            ds.startEntry("cacheStatus");
            searchMan.getBlackLabCache().dataStreamCacheStatus(ds);
            ds.endEntry();
        }
        ds.endMap();

        return HTTP_OK;
    }

}
