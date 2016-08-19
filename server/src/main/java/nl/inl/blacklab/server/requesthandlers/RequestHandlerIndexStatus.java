package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
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
	public boolean isCacheAllowed() {
		return false; // because status might change
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		//Searcher searcher = getSearcher();
		//IndexStructure struct = searcher.getIndexStructure();

		// Assemble response
		ds.startMap()
			.entry("indexName", indexName)
			.entry("status", indexMan.getIndexStatus(indexName))
		.endMap();

		return HTTP_OK;
	}

}
