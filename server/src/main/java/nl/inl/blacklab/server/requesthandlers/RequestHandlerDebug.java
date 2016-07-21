package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.search.User;

/**
 * Get debug info about the servlet and index.
 * Only available in debug mode (BlackLabServer.DEBUG_MODE == true)
 */
public class RequestHandlerDebug extends RequestHandler {
	public RequestHandlerDebug(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() {
		DataObjectMapElement response = new DataObjectMapElement();
		response.put("indexName", indexName);
		response.put("resource", urlResource);
		response.put("rest", urlPathInfo);
		response.put("queryString", request.getQueryString());
		response.put("searchParam", servlet.getSearchParameters(request, indexName).toString());
		Response r = new Response(response);
		r.setCacheAllowed(false);
		return r;
	}

}
