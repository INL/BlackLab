package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Request handler for grouped hit results.
 */
public class RequestHandlerHitsGrouped extends RequestHandler {

    public RequestHandlerHitsGrouped(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.HITS_GROUPED);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        WebserviceRequestHandler.opHits(params, ds);
        return HTTP_OK;
    }

}
