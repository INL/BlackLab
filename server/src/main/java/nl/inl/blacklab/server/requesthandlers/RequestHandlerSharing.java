package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.requests.WebserviceOperations;

/**
 * Get and change sharing options for a user corpus.
 */
public class RequestHandlerSharing extends RequestHandler {

    public RequestHandlerSharing(BlackLabServer servlet,
            HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        debug(logger, "REQ sharing: " + indexName);

        // If POST request with 'users' parameter: update the list of users to share with
        if (request.getMethod().equals("POST")) {
            String[] users = request.getParameterValues("users[]");
            if (users == null)
                users = new String[0];
            WebserviceOperations.setUsersToShareWith(user, indexMan, indexName, users);
            return Response.success(ds, "Index shared with specified user(s).");
        }

        // Regular request: return the list of users this corpus is shared with
        List<String> shareWithUsers = WebserviceOperations.getUsersToShareWith(user, indexMan, indexName);
        ds.startMap().startEntry("users[]").startList();
        for (String userId : shareWithUsers) {
            ds.item("user", userId);
        }
        ds.endList().endEntry().endMap();
        return HTTP_OK;
    }

}
