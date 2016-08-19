package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Get information about the structure of an index.
 */
public class RequestHandlerDocSnippet extends RequestHandler {
	public RequestHandlerDocSnippet(BlackLabServer servlet, HttpServletRequest request, User user, String indexName, String urlResource, String urlPathPart) {
		super(servlet, request, user, indexName, urlResource, urlPathPart);
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
		Document document = searcher.document(luceneDocId);
		if (document == null)
			throw new InternalServerError("Couldn't fetch document with pid '" + docId + "'.", 24);

		Hit hit;
		int wordsAroundHit;
		int start, end;
		boolean isHit = false;
		if (searchParam.containsKey("hitstart")) {
			start = searchParam.getInteger("hitstart");
			end = searchParam.getInteger("hitend");
			wordsAroundHit = searchParam.getInteger("wordsaroundhit");
			isHit = true;
		} else {
			start = searchParam.getInteger("wordstart");
			end = searchParam.getInteger("wordend");
			wordsAroundHit = 0;
		}
		int snippetStart = Math.max(0, start - wordsAroundHit);
		int snippetEnd = end + wordsAroundHit;
		if (snippetEnd - snippetStart > searchMan.config().maxSnippetSize()) {
			throw new BadRequest("SNIPPET_TOO_LARGE", "Snippet too large. Maximum size for a snippet is " + searchMan.config().maxSnippetSize() + " words.");
		}
		if (start < 0 || end < 0 || wordsAroundHit * 2 + end - start <= 0 || end < start || wordsAroundHit < 0) {
			throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
		}
		hit = new Hit(luceneDocId, start, end);
		Hits hits = Hits.fromList(searcher, Arrays.asList(hit));
		boolean origContent = searchParam.getString("usecontent").equals("orig");
		hits.settings().setConcordanceType(origContent ? ConcordanceType.CONTENT_STORE : ConcordanceType.FORWARD_INDEX);
		getHitOrFragmentInfo(ds, hits, hit, wordsAroundHit, origContent, !isHit, null);
		return HTTP_OK;
	}

	/**
	 * Get a DataObject representation of a hit
	 * (or just a document fragment with no hit in it)
	 *
	 * @param ds output stream
	 * @param hits the hits object the hit occurs in
	 * @param hit the hit (or fragment)
	 * @param wordsAroundHit number of words around the hit we want
	 * @param useOrigContent if true, uses the content store; if false, the forward index
	 * @param isFragment if false, separates hit into left/match/right; otherwise, just returns whole fragment
	 * @param docPid if not null, include doc pid, hit start and end info
	 */
	public static void getHitOrFragmentInfo(DataStream ds, Hits hits, Hit hit, int wordsAroundHit,
			boolean useOrigContent, boolean isFragment, String docPid) {
		ds.startMap();
		if (docPid != null) {
			// Add basic hit info
			ds	.entry("docPid", docPid)
				.entry("start", hit.start)
				.entry("end", hit.end);
		}

		if (useOrigContent) {
			Concordance c = hits.getConcordance(hit, wordsAroundHit);
			if (!isFragment) {
				ds	.startEntry("left").plain(c.left()).endEntry()
					.startEntry("match").plain(c.match()).endEntry()
					.startEntry("right").plain(c.right()).endEntry();
			} else {
				ds.plain(c.match());
			}
		} else {
			Kwic c = hits.getKwic(hit, wordsAroundHit);
			if (!isFragment) {
				ds	.startEntry("left").contextList(c.getProperties(), c.getLeft()).endEntry()
					.startEntry("match").contextList(c.getProperties(), c.getMatch()).endEntry()
					.startEntry("right").contextList(c.getProperties(), c.getRight()).endEntry();
			} else {
				ds.contextList(c.getProperties(), c.getTokens());
			}
		}
		ds.endMap();
	}

	@Override
	protected boolean isDocsOperation() {
		return true;
	}

}
