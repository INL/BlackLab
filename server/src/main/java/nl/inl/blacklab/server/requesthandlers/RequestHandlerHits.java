package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Request handler for hit results.
 */
public class RequestHandlerHits extends RequestHandler {

    public RequestHandlerHits(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.HITS);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        /*
        if (params.isCalculateCollocations()) {
            // Collocations request
            TermFrequencyList tfl = WebserviceOperations.calculateCollocations(params);
            DStream.collocationsResponse(ds, tfl);
        } else {
            // Hits request
            ResultHits resultHits = WebserviceOperations.getResultHits(params);
            DStream.hitsResponse(ds, resultHits);
        }*/
        WebserviceRequestHandler.opHits(params, ds);
        return HTTP_OK;
    }

}
