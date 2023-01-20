package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.WebserviceRequestHandler;

/**
 * Get a snippet of a document's contents.
 */
public class RequestHandlerDocSnippet extends RequestHandler {
    public RequestHandlerDocSnippet(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (docPid.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");
        params.setDocPid(docPid);

        WebserviceRequestHandler.opDocSnippet(params, ds);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
