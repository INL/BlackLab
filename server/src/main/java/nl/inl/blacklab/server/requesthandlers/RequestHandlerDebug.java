package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.util.ServletUtil;

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
        QueryParamsBlackLabServer params = new QueryParamsBlackLabServer(indexName, request, searchMan, user);
        WebserviceParams searchParameters = WebserviceParamsImpl.get(false, isDebugMode, params);
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
