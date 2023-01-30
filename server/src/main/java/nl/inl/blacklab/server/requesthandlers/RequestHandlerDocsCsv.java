package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Handle /docs requests that produce CSV.
 */
public class RequestHandlerDocsCsv extends RequestHandler {

    public RequestHandlerDocsCsv(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOCS_CSV);
    }

    @Override
    public int handle(DataStream ds) throws BlsException, InvalidQuery {
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
