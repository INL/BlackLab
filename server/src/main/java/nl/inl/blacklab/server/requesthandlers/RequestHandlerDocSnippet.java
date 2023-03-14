package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.results.ResponseStreamer;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;
import nl.inl.blacklab.webservice.WebserviceOperation;

/**
 * Get a snippet of a document's contents.
 */
public class RequestHandlerDocSnippet extends RequestHandler {
    public RequestHandlerDocSnippet(UserRequestBls userRequest) {
        super(userRequest, WebserviceOperation.DOC_SNIPPET);
    }

    @Override
    public int handle(ResponseStreamer rs) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (docPid.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");
        params.setDocPid(docPid);

        WebserviceRequestHandler.opDocSnippet(params, rs);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
