package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * List documents, search for documents matching criteria.
 */
public class RequestHandlerDocs extends RequestHandler {

    public RequestHandlerDocs(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.DOCS);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
        /*
        // Do we want to view a single group after grouping?
        ResultDocsResponse result;
        if (params.getGroupProps().isPresent() && params.getViewGroup().isPresent()) {
            // View a single group in a grouped docs resultset
            result = WebserviceOperations.viewGroupDocsResponse(params);
        } else {
            // Regular set of docs (no grouping first)
            result = WebserviceOperations.regularDocsResponse(params);
        }
        DStream.docsResponse(ds, result);*/
        WebserviceRequestHandler.opDocs(params, ds);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
