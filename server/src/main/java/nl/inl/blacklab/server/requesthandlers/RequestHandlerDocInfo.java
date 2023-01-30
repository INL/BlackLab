package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Get information about a document.
 */
public class RequestHandlerDocInfo extends RequestHandler {

    public RequestHandlerDocInfo(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOC_INFO);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        params.setDocPid(docPid);

        debug(logger, "REQ doc info: " + indexName + "-" + params.getDocPid());

        WebserviceRequestHandler.opDocInfo(params, ds);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
