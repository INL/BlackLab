package nl.inl.blacklab.server.requesthandlers;

import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ResultNotFound;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.HitsImpl;
import nl.inl.blacklab.search.results.HitsSettings;
import nl.inl.blacklab.search.results.HitsWindow;
import nl.inl.blacklab.search.results.Kwics;
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
    public RequestHandlerDocSnippet(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String docId = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (docId.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");

        BlackLabIndex blIndex = blIndex();
        int luceneDocId = BlsUtils.getDocIdFromPid(blIndex, docId);
        if (luceneDocId < 0)
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
        Document document = blIndex.doc(luceneDocId).luceneDoc();
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

        if (start < 0 || end < 0 || wordsAroundHit * 2 + end - start <= 0 || end < start || wordsAroundHit < 0) {
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        }

        // Clamp snippet to max size
        int snippetStart = Math.max(0, start - wordsAroundHit);
        int snippetEnd = end + wordsAroundHit;
        if (snippetEnd - snippetStart > searchMan.config().maxSnippetSize()) {
            int clampedWindow = Math.max(0, (searchMan.config().maxSnippetSize() - (end - start)) / 2);
            snippetStart = Math.max(0, start - clampedWindow);
            snippetEnd = end + clampedWindow;
//			throw new BadRequest("SNIPPET_TOO_LARGE", "Snippet too large. Maximum size for a snippet is " + searchMan.config().maxSnippetSize() + " words.");
        }
        hit = Hit.create(luceneDocId, start, end);
        boolean origContent = searchParam.getString("usecontent").equals("orig");
        ConcordanceType concType = origContent ? ConcordanceType.CONTENT_STORE : ConcordanceType.FORWARD_INDEX;
        HitsSettings settings = blIndex.hitsSettings().withConcordanceType(concType);
        HitsImpl hits = Hits.fromList(blIndex, blIndex.mainAnnotatedField(), Arrays.asList(hit), settings);
        getHitOrFragmentInfo(ds, hits, hit, wordsAroundHit, origContent, !isHit, null);
        return HTTP_OK;
    }

    /**
     * Get a DataObject representation of a hit (or just a document fragment with no
     * hit in it)
     *
     * @param ds output stream
     * @param hits the hits object the hit occurs in
     * @param hit the hit (or fragment)
     * @param wordsAroundHit number of words around the hit we want
     * @param useOrigContent if true, uses the content store; if false, the forward
     *            index
     * @param isFragment if false, separates hit into left/match/right; otherwise,
     *            just returns whole fragment
     * @param docPid if not null, include doc pid, hit start and end info
     */
    public static void getHitOrFragmentInfo(DataStream ds, HitsImpl hits, Hit hit, int wordsAroundHit,
            boolean useOrigContent, boolean isFragment, String docPid) {
        ds.startMap();
        if (docPid != null) {
            // Add basic hit info
            ds.entry("docPid", docPid)
                    .entry("start", hit.start())
                    .entry("end", hit.end());
        }

        try {
            HitsWindow singleHit = hits.window(hit);
            if (useOrigContent) {
                Concordances concordances = singleHit.concordances(wordsAroundHit);
                Concordance c = concordances.get(hit);
                if (!isFragment) {
                    ds.startEntry("left").plain(c.left()).endEntry()
                            .startEntry("match").plain(c.match()).endEntry()
                            .startEntry("right").plain(c.right()).endEntry();
                } else {
                    ds.plain(c.match());
                }
            } else {
                Kwics kwics = singleHit.kwics(wordsAroundHit);
                Kwic c = kwics.get(hit);
                if (!isFragment) {
                    ds.startEntry("left").contextList(c.getProperties(), c.getLeft()).endEntry()
                            .startEntry("match").contextList(c.getProperties(), c.getMatch()).endEntry()
                            .startEntry("right").contextList(c.getProperties(), c.getRight()).endEntry();
                } else {
                    ds.contextList(c.getProperties(), c.getTokens());
                }
            }
        } catch (ResultNotFound e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        ds.endMap();
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
