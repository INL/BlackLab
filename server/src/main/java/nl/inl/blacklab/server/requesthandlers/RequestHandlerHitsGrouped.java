package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Request handler for grouped hit results.
 */
public class RequestHandlerHitsGrouped extends RequestHandler {

    public RequestHandlerHitsGrouped(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.HITS_GROUPED);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        WebserviceRequestHandler.opHits(params, ds);
//        ResultHitsGrouped hitsGrouped = WebserviceOperations.hitsGrouped(params);
//        DStream.hitsGroupedResponse(ds, hitsGrouped);
        return HTTP_OK;
    }

}
