package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.DocIndexerFactoryUserFormats;
import nl.inl.blacklab.server.jobs.User;

/**
 * Delete an input format configuration.
 */
public class RequestHandlerDeleteFormat extends RequestHandler {

	public RequestHandlerDeleteFormat(BlackLabServer servlet,
			HttpServletRequest request, User user, String indexName,
			String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		debug(logger, "REQ add format: " + indexName);

		// Get the uploaded file parameters
        String formatIdentifier = urlResource; //request.getParameter("format");

        DocIndexerFactoryUserFormats formatMan = searchMan.getIndexManager().getUserFormatManager();
        if (formatMan == null)
			throw new BadRequest("CANNOT_DELETE_INDEX ", "Could not delete format. The server is not configured with support for user content.");

        formatMan.deleteUserFormat(user, formatIdentifier);
		return Response.success(ds, "Format deleted.");
	}
}
