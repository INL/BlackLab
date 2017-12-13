package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
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

	public RequestHandlerServerInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
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
			.entry("blacklabBuildTime", Searcher.getBlackLabBuildTime())
			.entry("blacklabVersion", Searcher.getBlackLabVersion());

		ds.startEntry("indices").startMap();

		for (Index index: indices) {
			ds.startAttrEntry("index", "name", index.getId());
			ds.startMap();

			synchronized (index) {
				IndexStructure struct = index.getIndexStructure();
				IndexStatus status = index.getStatus();

				ds.entry("displayName", struct.getDisplayName());
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

				String documentFormat = struct.getDocumentFormat();
				if (documentFormat != null && documentFormat.length() > 0)
					ds.entry("documentFormat", documentFormat);
				ds.entry("timeModified", struct.getTimeModified());
				if (struct.getTokenCount() > 0)
					ds.entry("tokenCount", struct.getTokenCount());

			}

			ds.endMap();
			ds.endAttrEntry();
		}
		ds.endMap().endEntry();

		ds.startEntry("user").startMap();
		ds.entry("loggedIn", user.isLoggedIn());
		if (user.isLoggedIn())
			ds.entry("id", user.getUserId());
		boolean canCreateIndex = user.isLoggedIn() ? indexMan.canCreateIndex(user.getUserId()) : false;
        ds.entry("canCreateIndex", canCreateIndex);
		ds.endMap().endEntry();

		ds.entry("helpPageUrl", servlet.getServletContext().getContextPath() + "/help");
		if (debugMode) {
			ds.startEntry("cacheStatus");
			searchMan.getCache().dataStreamCacheStatus(ds);
			ds.endEntry();
		}
		ds.endMap();

		return HTTP_OK;
	}


}
