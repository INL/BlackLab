package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Delete an input format configuration.
 */
public class RequestHandlerDeleteFormat extends RequestHandler {

    public RequestHandlerDeleteFormat(UserRequestBls userRequest, String indexName, String urlResource,
            String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.DELETE_INPUT_FORMAT);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        debug(logger, "REQ add format: " + indexName);
        WebserviceOperations.deleteUserFormat(params, urlResource);
        return Response.success(ds, "Format deleted.");
    }

}
