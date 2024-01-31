package nl.inl.blacklab.server.requesthandlers;

import jakarta.servlet.http.HttpServletResponse;

import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.Response;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Create a user index.
 */
public class RequestHandlerCreateIndex extends RequestHandler {
    public RequestHandlerCreateIndex(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CREATE_CORPUS);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        // Create index and return success
        try {
            String newIndexName = request.getParameter("name");
            if (newIndexName == null || newIndexName.length() == 0)
                return Response.badRequest(rs, "ILLEGAL_INDEX_NAME", "You didn't specify the required name parameter.");
            String displayName = request.getParameter("display");
            String formatIdentifier = request.getParameter("format");

            debug(logger, "REQ create index: " + newIndexName + ", " + displayName + ", " + formatIdentifier);
            if (!user.isLoggedIn() || !newIndexName.startsWith(user.getUserId() + ":")) {
                logger.debug("(forbidden, cannot create index in another user's area)");
                return Response.forbidden(rs, "You can only create indices in your own private area.");
            }

            indexMan.createIndex(user, newIndexName, displayName, formatIdentifier);

            return Response.status(rs, "SUCCESS", "Index created succesfully.", HttpServletResponse.SC_CREATED);
        } catch (BlsException e) {
            throw e;
        } catch (Exception e) {
            return Response.internalError(rs, e, debugMode, "INTERR_CREATING_INDEX");
        }
    }
}
