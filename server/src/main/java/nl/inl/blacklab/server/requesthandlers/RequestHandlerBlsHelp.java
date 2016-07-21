package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.search.User;

/**
 * Get information about this BlackLab server.
 */
public class RequestHandlerBlsHelp extends RequestHandler {

	public RequestHandlerBlsHelp(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() {
		DataObjectMapElement response = new DataObjectMapElement();
		String urlPrefix = servlet.getServletContext().getContextPath() + "/";
		String urlIndexPrefix = servlet.getServletContext().getContextPath() + "/myIndexName/";
		response.put("readme", "This simple help response gives some examples of BlackLab Server URLs. For the complete documentation, please refer to the official project page on GitHub (https://github.com/INL/BlackLab-server)");
		response.put("serverInfo", urlPrefix);
		response.put("indexInfo", urlIndexPrefix);
		response.put("detailedFieldInfo", urlIndexPrefix + "fields/myFieldName");
		response.put("searchHits", urlIndexPrefix + "hits?patt=%22dog%22&filter=author:barker&number=50&wordsaroundhit=8");
		response.put("searchDocs", urlIndexPrefix + "docs?patt=%22dog%22&filter=author:barker&first=60&number=30");
		response.put("groupHitsByWordLeft", urlIndexPrefix + "hits?patt=%22dog%22&group=wordleft");
		response.put("groupDocsByPublisher", urlIndexPrefix + "docs?patt=%22dog%22&group=field:publisher");
		response.put("documentMetadata", urlIndexPrefix + "docs/MYDOCPID00001");
		response.put("documentOriginalContents", urlIndexPrefix + "docs/MYDOCPID00001/contents");
		response.put("documentSnippetAroundHit", urlIndexPrefix + "docs/MYDOCPID00001/snippet?hitstart=47&hitend=48&wordsaroundhit=8");

		return new Response(response);
	}


}
