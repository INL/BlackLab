package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.lib.User;

/**
 * Base class for handling CSV requests for hits and documents.
 */
public abstract class RequestHandlerCsvAbstract extends RequestHandler {
    public RequestHandlerCsvAbstract(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathInfo) {
        super(servlet, request, user, indexName, urlResource, urlPathInfo);
    }


}
