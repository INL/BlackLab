package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.jobs.User;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerClearCache extends RequestHandler {
	public RequestHandlerClearCache(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() {
		if (!debugMode)
			return Response.forbidden();
		searchMan.clearCache();
		return Response.status("SUCCESS", "Cache cleared succesfully.", HttpServletResponse.SC_OK);
	}

}
