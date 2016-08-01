package nl.inl.blacklab.server.requesthandlers;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectMapAttribute;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
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
	public Response handle() throws BlsException {
		Collection<String> indices = searchMan.getAllAvailableIndices(user.getUserId());
		DataObjectMapAttribute doIndices = new DataObjectMapAttribute("index", "name");
		//DataObjectList doIndices = new DataObjectList("index");
		for (String indexName: indices) {
			DataObjectMapElement doIndex = new DataObjectMapElement();
			Searcher searcher = searchMan.getSearcher(indexName);
			IndexStructure struct = searcher.getIndexStructure();
			doIndex.put("displayName", struct.getDisplayName());
			doIndex.put("status", searchMan.getIndexStatus(indexName));
			String documentFormat = struct.getDocumentFormat();
			if (documentFormat != null && documentFormat.length() > 0)
				doIndex.put("documentFormat", documentFormat);
			doIndex.put("timeModified", struct.getTimeModified());
			if (struct.getTokenCount() > 0)
				doIndex.put("tokenCount", struct.getTokenCount());
			doIndices.put(indexName, doIndex);
		}

		DataObjectMapElement doUser = new DataObjectMapElement();
		doUser.put("loggedIn", user.isLoggedIn());
		if (user.isLoggedIn())
			doUser.put("id", user.getUserId());
		doUser.put("canCreateIndex", user.isLoggedIn() ? searchMan.canCreateIndex(user.getUserId()) : false);

		DataObjectMapElement response = new DataObjectMapElement();
		response.put("blacklabBuildTime", Searcher.getBlackLabBuildTime());
		response.put("indices", doIndices);
		response.put("user", doUser);
		response.put("helpPageUrl", servlet.getServletContext().getContextPath() + "/help");
		if (debugMode) {
			response.put("cacheStatus", searchMan.getCache().getCacheStatusDataObject());
		}

		Response responseObj = new Response(response);
		responseObj.setCacheAllowed(false); // You can create/delete indices, don't cache the list
		return responseObj;
	}


}
