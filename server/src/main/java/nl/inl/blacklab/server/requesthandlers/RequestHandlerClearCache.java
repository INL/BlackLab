package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Clear the cache.
 */
public class RequestHandlerClearCache extends RequestHandler {
    public RequestHandlerClearCache(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.CACHE_CLEAR);
    }

    @Override
    public int handle(DStream ds) {
        return WebserviceRequestHandler.opClearCache(params, ds, debugMode);
    }

}
