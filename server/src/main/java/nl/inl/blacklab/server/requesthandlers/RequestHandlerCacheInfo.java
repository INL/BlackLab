package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.User;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerCacheInfo extends RequestHandler {
	public RequestHandlerCacheInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {

		String strDebugInfo = request.getParameter("debug");
		boolean debugInfo = strDebugInfo == null ? false : strDebugInfo.matches("true|yes|1");

		DataObjectMapElement response = new DataObjectMapElement();
		response.put("cacheStatus", searchMan.getCacheStatusDataObject());
		response.put("cacheContents", searchMan.getCacheContentsDataObject(debugInfo));

		Response r = new Response(response);
		r.setCacheAllowed(false);
		return r;
	}

}
