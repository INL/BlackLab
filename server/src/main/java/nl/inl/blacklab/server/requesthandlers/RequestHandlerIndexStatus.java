package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.Index.IndexStatus;
import nl.inl.blacklab.server.jobs.User;

/**
 * Get information about the status of an index.
 */
public class RequestHandlerIndexStatus extends RequestHandler {

    public RequestHandlerIndexStatus(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // because status might change
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        Index index = indexMan.getIndex(indexName);
        synchronized (index) {
            IndexStatus status = index.getStatus();
            IndexMetadata indexMetadata;
            try {
                indexMetadata = index.getIndexMetadata();
            } catch (IndexVersionMismatch e) {
                throw BlsException.indexVersionMismatch(e);
            }

            // Assemble response
            ds.startMap()
                    .entry("indexName", indexName)
                    .entry("displayName", indexMetadata.custom().get("displayName", ""))
                    .entry("description", indexMetadata.custom().get("description", ""))
                    .entry("status", status);

            String formatIdentifier = indexMetadata.documentFormat();
            if (formatIdentifier != null && formatIdentifier.length() > 0)
                ds.entry("documentFormat", formatIdentifier);
            ds.entry("timeModified", indexMetadata.timeModified());
            ds.entry("tokenCount", indexMetadata.tokenCount());

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

            ds.endMap();
        }

        return HTTP_OK;
    }

}
