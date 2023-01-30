package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * List documents, search for documents matching criteria.
 */
public class RequestHandlerDocs extends RequestHandler {

    public RequestHandlerDocs(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOCS);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        WebserviceRequestHandler.opDocs(params, ds);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
