package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.BlsCache;

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
    public int handle(DataStream ds) throws BlsException {
        String strDebugInfo = request.getParameter("debug");
        boolean debugInfo = strDebugInfo == null ? false : strDebugInfo.matches("true|yes|1");
        ds.startMap()
                .startEntry("cacheStatus");
        SearchCache blackLabCache = searchMan.getBlackLabCache();
        blackLabCache.getCacheStatus(ds);
        ds.endEntry()
            .startEntry("cacheContents");
        blackLabCache.getCacheContent(ds, debugInfo);
        ds.endEntry()
                .endMap();
        return HTTP_OK;
    }

}
