package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Clear the cache.
 */
public class RequestHandlerClearCache extends RequestHandler {
    public RequestHandlerClearCache(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CACHE_CLEAR);
    }

    @Override
    public int handle(DataStream ds) {
        return WebserviceRequestHandler.opClearCache(params, ds, debugMode);
    }

}
