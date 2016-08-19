package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
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
		Collection<String> indices = indexMan.getAllAvailableIndices(user.getUserId());

		ds.startMap()
			.entry("blacklabBuildTime", Searcher.getBlackLabBuildTime());

		ds.startEntry("indices").startMap();
		//DataObjectMapAttribute doIndices = new DataObjectMapAttribute("index", "name");
		for (String indexName: indices) {
			ds.startAttrEntry("index", "name", indexName);

			Searcher searcher = indexMan.getSearcher(indexName);
			IndexStructure struct = searcher.getIndexStructure();
			ds.startMap();
			ds.entry("displayName", struct.getDisplayName());
			ds.entry("status", indexMan.getIndexStatus(indexName));
			String documentFormat = struct.getDocumentFormat();
			if (documentFormat != null && documentFormat.length() > 0)
				ds.entry("documentFormat", documentFormat);
			ds.entry("timeModified", struct.getTimeModified());
			if (struct.getTokenCount() > 0)
				ds.entry("tokenCount", struct.getTokenCount());
			ds.endMap();

			ds.endAttrEntry();
		}
		ds.endMap().endEntry();

		ds.startEntry("user").startMap();
		ds.entry("loggedIn", user.isLoggedIn());
		if (user.isLoggedIn())
			ds.entry("id", user.getUserId());
		ds.entry("canCreateIndex", user.isLoggedIn() ? indexMan.canCreateIndex(user.getUserId()) : false);
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
