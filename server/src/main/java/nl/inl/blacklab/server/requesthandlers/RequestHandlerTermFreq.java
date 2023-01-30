package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Request handler for term frequencies for a set of documents.
 */
public class RequestHandlerTermFreq extends RequestHandler {

    public RequestHandlerTermFreq(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.TERMFREQ);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        WebserviceRequestHandler.opTermFreq(params, ds);
        return HTTP_OK;
    }

}
