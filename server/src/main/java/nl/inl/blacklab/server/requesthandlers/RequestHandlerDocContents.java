package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.dataobject.DataFormat;
import nl.inl.blacklab.server.dataobject.DataObjectPlain;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.search.JobWithHits;
import nl.inl.blacklab.server.search.SearchCache;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.User;

import org.apache.lucene.document.Document;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerDocContents extends RequestHandler {
	public RequestHandlerDocContents(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public Response handle() throws BlsException {
		int i = urlPathInfo.indexOf('/');
		String docId = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
		if (docId.length() == 0)
			throw new BadRequest("NO_DOC_ID", "Specify document pid.");

		Searcher searcher = getSearcher();
		DataFormat type = searchMan.getContentsFormat(indexName);
		int luceneDocId = SearchManager.getLuceneDocIdFromPid(searcher, docId);
		if (luceneDocId < 0)
			throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
		Document document = searcher.document(luceneDocId); //searchMan.getDocumentFromPid(indexName, docId);
		if (document == null)
			throw new InternalServerError("Couldn't fetch document with pid '" + docId + "'.", 9);
		if (!searcher.getIndexStructure().contentViewable()) {
			Response errObj = Response.unauthorized("Viewing the full contents of this document is not allowed.");
			errObj.setOverrideType(type); // Application expects this MIME type, don't disappoint
			return errObj;
		}

		String patt = searchParam.getString("patt");
		Hits hits = null;
		if (patt != null && patt.length() > 0) {
			//@@@ TODO: filter on document!
			searchParam.put("docpid", docId);
			JobWithHits search;
			search = (JobWithHits) searchMan.search(user, searchParam.hits());
			try {
				search.waitUntilFinished(SearchCache.maxSearchTimeSec);
				if (!search.finished()) {
					Response errObj = Response.searchTimedOut();
					errObj.setOverrideType(type); // Application expects this MIME type, don't disappoint
					return errObj;
				}
				hits = search.getHits();
			} finally {
				search.decrRef();
				search = null;
			}
		}

		String content;
		int startAtWord = searchParam.getInteger("wordstart");
		int endAtWord = searchParam.getInteger("wordend");
		if (startAtWord < -1 || endAtWord < -1 || (startAtWord >= 0 && endAtWord >= 0 && endAtWord <= startAtWord) ) {
			throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
		}

		// Note: we use the highlighter regardless of whether there's hits because
		// it makes sure our document fragment is well-formed.
		Hits hitsInDoc = hits == null ? null : hits.getHitsInDoc(luceneDocId);
		content = searcher.highlightContent(luceneDocId, searcher.getMainContentsFieldName(), hitsInDoc, startAtWord, endAtWord);

		DataObjectPlain docContents = new DataObjectPlain(content, type);
		if (startAtWord == -1 && endAtWord == -1) {
			// Full document; no need for another root element
			docContents.setAddRootElement(false); // don't add another root element
		}
		return new Response(docContents);
	}
}
