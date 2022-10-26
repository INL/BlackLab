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
        boolean includeDebugInfo = params.isIncludeDebugInfo();
        dstreamCacheInfo(ds, searchMan.getBlackLabCache(), includeDebugInfo);
        return HTTP_OK;
    }

    private void dstreamCacheInfo(DataStream ds, SearchCache blackLabCache, boolean includeDebugInfo) {
        ds.startMap()
                .startEntry("cacheStatus");
        ds.value(blackLabCache.getStatus());
        ds.endEntry()
            .startEntry("cacheContents");
        ds.value(blackLabCache.getContents(includeDebugInfo));
        ds.endEntry()
                .endMap();
    }
}
