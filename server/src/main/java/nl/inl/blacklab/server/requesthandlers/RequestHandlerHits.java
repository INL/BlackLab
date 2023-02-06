package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {

    public RequestHandlerHits(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.HITS);
    }

    @Override
    public int handle(DStream ds) throws BlsException, InvalidQuery {
        WebserviceRequestHandler.opHits(params, ds);
        return HTTP_OK;
    }

}
