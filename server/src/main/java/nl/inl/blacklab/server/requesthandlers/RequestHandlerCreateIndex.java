package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerCreateIndex extends RequestHandler {
    public RequestHandlerCreateIndex(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        // Create index and return success
        try {
            String newIndexName = request.getParameter("name");
            if (newIndexName == null || newIndexName.length() == 0)
                return Response.badRequest(ds, "ILLEGAL_INDEX_NAME", "You didn't specify the required name parameter.");
            String displayName = request.getParameter("display");
            String formatIdentifier = request.getParameter("format");

            debug(logger, "REQ create index: " + newIndexName + ", " + displayName + ", " + formatIdentifier);
            if (!user.isLoggedIn() || !newIndexName.startsWith(user.getUserId() + ":")) {
                logger.debug("(forbidden, cannot create index in another user's area)");
                return Response.forbidden(ds, "You can only create indices in your own private area.");
            }

            indexMan.createIndex(user, newIndexName, displayName, formatIdentifier);

            return Response.status(ds, "SUCCESS", "Index created succesfully.", HttpServletResponse.SC_CREATED);
        } catch (BlsException e) {
            throw e;
        } catch (Exception e) {
            return Response.internalError(ds, e, debugMode, "INTERR_CREATING_INDEX");
        }
    }
}
