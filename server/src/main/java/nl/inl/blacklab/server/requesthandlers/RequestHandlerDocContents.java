package nl.inl.blacklab.server.requesthandlers;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.JobWithHits;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerDocContents extends RequestHandler {
	public RequestHandlerDocContents(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
	}

	@Override
	public DataFormat getOverrideType() {
		// Application expects this MIME type, don't disappoint
		return DataFormat.XML;
	}

	@Override
	public boolean omitBlackLabResponseRootElement() {
		int startAtWord = searchParam.getInteger("wordstart");
		int endAtWord = searchParam.getInteger("wordend");
		if (startAtWord < -1 || endAtWord < -1 || (startAtWord >= 0 && endAtWord >= 0 && endAtWord <= startAtWord) ) {
			// Illegal value; error will be thrown, will need a root element
			return false;
		}
		if (startAtWord == -1 && endAtWord == -1) {
			// Full document; no need for another root element
			return true;
		}
		return false;
	}

	@Override
	public int handle(DataStream ds) throws BlsException {
		int i = urlPathInfo.indexOf('/');
		String docId = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
		if (docId.length() == 0)
			throw new BadRequest("NO_DOC_ID", "Specify document pid.");

		Searcher searcher = getSearcher();
		int luceneDocId = BlsUtils.getLuceneDocIdFromPid(searcher, docId);
		if (luceneDocId < 0)
			throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
		Document document = searcher.document(luceneDocId); //searchMan.getDocumentFromPid(indexName, docId);
		if (document == null)
			throw new InternalServerError("Couldn't fetch document with pid '" + docId + "'.", 9);
		if (!searcher.getIndexStructure().contentViewable()) {
			return Response.unauthorized(ds, "Viewing the full contents of this document is not allowed.");
		}

		Hits hits = null;
		if (searchParam.hasPattern()) {
			//@@@ TODO: filter on document!
			searchParam.put("docpid", docId);
			JobWithHits search;
			search = (JobWithHits) searchMan.search(user, searchParam.hits(), true);
			try {
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

		ds.plain(content);
		return HTTP_OK;
	}

	@Override
	protected boolean isDocsOperation() {
		return true;
	}
}
