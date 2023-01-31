package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerCacheInfo extends RequestHandler {
    public RequestHandlerCacheInfo(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CACHE_INFO);
    }

    @Override
    public boolean isCacheAllowed() {
        return false;
    }

    @Override
    public int handle(DataStream ds) {
        WebserviceRequestHandler.opCacheInfo(params, ds);
        return HTTP_OK;
    }

}
