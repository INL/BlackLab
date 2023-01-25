package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.lib.WebserviceOperation;

/**
 * Display the contents of the cache.
 */
public class RequestHandlerCacheInfo extends RequestHandler {
    public RequestHandlerCacheInfo(UserRequestBls userRequest, String indexName,
            String urlResource, String urlPathPart) {
        super(userRequest, indexName, urlResource, urlPathPart, WebserviceOperation.CACHE_INFO);
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
