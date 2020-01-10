package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.jobs.User;

/**
 * Display the contents of the cache.
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

        Index index = indexMan.getIndex(indexName);

        // If POST request with 'users' parameter: update the list of users to share with
        if (request.getMethod().equals("POST")) {
            if (!index.isUserIndex() || (!index.userMayRead(user)))
                throw new NotAuthorized("You can only share your own private indices with others.");
            // Update the list of users to share with
            String[] users = request.getParameterValues("users[]");
            if (users == null)
                users = new String[0];
            List<String> shareWithUsers = Arrays.asList(users).stream().map(String::trim).collect(Collectors.toList());
            index.setShareWithUsers(shareWithUsers);
            return Response.success(ds, "Index shared with specified user(s).");
        }

        // Regular request: return the list of users this corpus is shared with
        if (!index.userMayRead(user))
            throw new NotAuthorized("You are not authorized to access this index.");
        List<String> shareWithUsers = index.getShareWithUsers();
        ds.startMap().startEntry("users[]").startList();
        for (String userId : shareWithUsers) {
            ds.item("user", userId);
        }
        ds.endList().endEntry().endMap();
        return HTTP_OK;
    }

}
