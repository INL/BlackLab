package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.WebserviceOperation;

/**
 * Clear the cache.
 */
public class RequestHandlerClearCache extends RequestHandler {
    public RequestHandlerClearCache(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.CLEAR_CACHE);
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
