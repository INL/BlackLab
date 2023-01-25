package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.WebserviceOperation;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.util.ServletUtil;

/**
 * Get debug info about the servlet and index. Only available in debug mode.
 */
public class RequestHandlerDebug extends RequestHandler {
    public RequestHandlerDebug(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.DEBUG);
    }

    @Override
    public boolean isCacheAllowed() {
        return false;
    }

    @Override
    public int handle(DataStream ds) {
        boolean isDebugMode = searchMan.isDebugMode(ServletUtil.getOriginatingAddress(request));
        QueryParamsBlackLabServer params = new QueryParamsBlackLabServer(indexName, searchMan, user, request, WebserviceOperation.DEBUG);
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
