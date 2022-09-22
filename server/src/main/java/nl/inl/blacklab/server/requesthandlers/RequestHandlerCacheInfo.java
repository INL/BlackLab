package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.User;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerCacheInfo extends RequestHandler {
    public RequestHandlerCacheInfo(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public boolean isCacheAllowed() {
        return false;
    }

    @Override
    public int handle(DataStream ds) {
        String strDebugInfo = request.getParameter("debug");
        boolean debugInfo = strDebugInfo != null && strDebugInfo.matches("true|yes|1");
        ds.startMap()
                .startEntry("cacheStatus");
        SearchCache blackLabCache = searchMan.getBlackLabCache();
        ds.value(blackLabCache.getCacheStatus());
        ds.endEntry()
            .startEntry("cacheContents");
        ds.value(blackLabCache.getCacheContent(debugInfo));
        ds.endEntry()
                .endMap();
        return HTTP_OK;
    }

}
