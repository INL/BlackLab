package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.Index.IndexStatus;
import nl.inl.blacklab.server.jobs.User;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerIndexStatus extends RequestHandler {

	public RequestHandlerIndexStatus(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
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
			IndexStructure struct = index.getIndexStructure();

			// Assemble response
			ds.startMap()
				.entry("indexName", indexName)
				.entry("displayName", index.getIndexStructure().getDisplayName())
				.entry("status", status);

			String documentFormat = struct.getDocumentFormat();
			if (documentFormat != null && documentFormat.length() > 0)
				ds.entry("documentFormat", documentFormat);
			ds.entry("timeModified", struct.getTimeModified());
			if (struct.getTokenCount() > 0)
				ds.entry("tokenCount", struct.getTokenCount());

			if (status.equals(IndexStatus.INDEXING)) {
				IndexListener indexProgress = index.getIndexer(true).getListener();
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
