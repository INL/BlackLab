package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Get information about the status of an index.
 */
public class RequestHandlerIndexStatus extends RequestHandler {

    public RequestHandlerIndexStatus(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.CORPUS_STATUS);
    }

    @Override
    public boolean isCacheAllowed() {
        return false; // because status might change
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        WebserviceRequestHandler.opCorpusStatus(params, ds);
        return HTTP_OK;
    }

}
