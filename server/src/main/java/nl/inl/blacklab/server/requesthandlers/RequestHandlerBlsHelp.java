package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.jobs.User;

/**
 * Get information about this BlackLab server.
 */
public class RequestHandlerBlsHelp extends RequestHandler {

    public RequestHandlerBlsHelp(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) {
        String urlPrefix = servlet.getServletContext().getContextPath() + "/";
        String urlIndexPrefix = servlet.getServletContext().getContextPath() + "/myIndexName/";
        ds.startMap()
                .entry("readme",
                        "This simple help response gives some examples of BlackLab Server URLs. For the complete documentation, please refer to the official project page on GitHub (https://github.com/INL/BlackLab)")
                .entry("serverInfo", urlPrefix)
                .entry("indexInfo", urlIndexPrefix)
                .entry("detailedFieldInfo", urlIndexPrefix + "fields/myFieldName")
                .entry("searchHits",
                        urlIndexPrefix + "hits?patt=%22dog%22&filter=author:barker&number=50&wordsaroundhit=8")
                .entry("searchDocs", urlIndexPrefix + "docs?patt=%22dog%22&filter=author:barker&first=60&number=30")
                .entry("groupHitsByWordLeft", urlIndexPrefix + "hits?patt=%22dog%22&group=wordleft")
                .entry("groupDocsByPublisher", urlIndexPrefix + "docs?patt=%22dog%22&group=field:publisher")
                .entry("documentMetadata", urlIndexPrefix + "docs/MYDOCPID00001")
                .entry("documentOriginalContents", urlIndexPrefix + "docs/MYDOCPID00001/contents")
                .entry("documentSnippetAroundHit",
                        urlIndexPrefix + "docs/MYDOCPID00001/snippet?hitstart=47&hitend=48&wordsaroundhit=8")
                .endMap();
        return HTTP_OK;
    }

}
