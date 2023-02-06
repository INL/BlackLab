package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.DStream;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Handle /docs requests that produce CSV.
 */
public class RequestHandlerDocsCsv extends RequestHandler {

    public RequestHandlerDocsCsv(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOCS_CSV);
    }

    @Override
    public int handle(DStream ds) throws BlsException, InvalidQuery {
        WebserviceRequestHandler.opDocsCsv(params, ds);
        return HTTP_OK;
    }

    @Override
    public DataFormat getOverrideType() {
        return DataFormat.CSV;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }
}
