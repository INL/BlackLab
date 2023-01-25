package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Get and change sharing options for a user corpus.
 */
public class RequestHandlerSharing extends RequestHandler {

    public RequestHandlerSharing(UserRequestBls userRequest, String indexName, String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.CORPUS_SHARING);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        debug(logger, "REQ sharing: " + indexName);

        // If POST request with 'users' parameter: update the list of users to share with
        if (request.getMethod().equals("POST")) {
            String[] users = request.getParameterValues("users[]");
            if (users == null)
                users = new String[0];
            WebserviceOperations.setUsersToShareWith(params, users);
            return Response.success(ds, "Index shared with specified user(s).");
        }

        // Regular request: return the list of users this corpus is shared with
        List<String> shareWithUsers = WebserviceOperations.getUsersToShareWith(params);
        dstreamUsersResponse(ds, shareWithUsers);
        return HTTP_OK;
    }

    private void dstreamUsersResponse(DataStream ds, List<String> shareWithUsers) {
        ds.startMap().startEntry("users[]").startList();
        for (String userId : shareWithUsers) {
            ds.item("user", userId);
        }
        ds.endList().endEntry().endMap();
    }

}
