package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.jobs.User;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerClearCache extends RequestHandler {
    public RequestHandlerClearCache(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) {
        if (!debugMode)
            return Response.forbidden(ds);
//        searchMan.getCache().clearCache(false);
        searchMan.getBlackLabCache().clear(false);
        return Response.status(ds, "SUCCESS", "Cache cleared succesfully.", HTTP_OK);
    }

}
