package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerIndexStatus extends RequestHandler {

	public RequestHandlerIndexStatus(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {
		//Searcher searcher = getSearcher();
		//IndexStructure struct = searcher.getIndexStructure();

		// Assemble response
		DataObjectMapElement response = new DataObjectMapElement();
		response.put("indexName", indexName);
		response.put("status", indexMan.getIndexStatus(indexName));

		// Remove any empty settings
		response.removeEmptyMapValues();

		Response r = new Response(response);
		r.setCacheAllowed(false); // because status might change
		return r;
	}

}
