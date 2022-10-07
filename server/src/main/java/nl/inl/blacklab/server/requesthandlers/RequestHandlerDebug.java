package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchCreatorImpl;
import nl.inl.blacklab.server.lib.User;

/**
 * Get debug info about the servlet and index. Only available in debug mode.
 */
public class RequestHandlerDebug extends RequestHandler {
    public RequestHandlerDebug(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false;
    }

    @Override
    public int handle(DataStream ds) {
        boolean isDebugMode = searchMan.isDebugMode(ServletUtil.getOriginatingAddress(request));
        BlackLabServerParams params = new BlackLabServerParams(indexName, request);
        SearchCreator searchParameters = SearchCreatorImpl.get(servlet.getSearchManager(), false,
                isDebugMode, params, user);
        ds.startMap()
                .entry("indexName", indexName)
                .entry("resource", urlResource)
                .entry("rest", urlPathInfo)
                .entry("queryString", request.getQueryString())
                .entry("searchParam", searchParameters.toString())
                .endMap();
        return HTTP_OK;
    }

}
