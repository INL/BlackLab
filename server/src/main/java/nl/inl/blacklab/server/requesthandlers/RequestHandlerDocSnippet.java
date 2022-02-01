package nl.inl.blacklab.server.requesthandlers;

import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Hits.HitsArrays;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.search.results.QueryInfo;
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
            throw new InternalServerError("Couldn't fetch document with pid '" + docId + "'.", "INTERR_FETCHING_DOCUMENT_SNIPPET");

        ContextSize wordsAroundHit;
        int start, end;
        boolean isHit = false;
        if (searchParam.containsKey("hitstart")) {
            start = searchParam.getInteger("hitstart");
            end = searchParam.getInteger("hitend");
            wordsAroundHit = ContextSize.get(searchParam.getInteger("wordsaroundhit"));
            isHit = true;
        } else {
            start = searchParam.getInteger("wordstart");
            end = searchParam.getInteger("wordend");
            wordsAroundHit = ContextSize.hitOnly();
        }

        if (start < 0 || end < 0 || wordsAroundHit.left() < 0 || wordsAroundHit.right() < 0 || start > end) {
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        }

        // Clamp snippet to max size
        int snippetStart = Math.max(0, start - wordsAroundHit.left());
        int snippetEnd = end + wordsAroundHit.right();
        int maxContextSize = searchMan.config().getParameters().getContextSize().getMax();
        if (snippetEnd - snippetStart > maxContextSize) {
            int clampedWindow = Math.max(0, (maxContextSize - (end - start)) / 2);
            snippetStart = Math.max(0, start - clampedWindow);
            snippetEnd = end + clampedWindow;
//			throw new BadRequest("SNIPPET_TOO_LARGE", "Snippet too large. Maximum size for a snippet is " + searchMan.config().maxSnippetSize() + " words.");
        }
        HitsArrays hitsArrays = new HitsArrays();
        hitsArrays.add(luceneDocId, start, end);
        boolean origContent = searchParam.getString("usecontent").equals("orig");
        Hits hits = Hits.fromList(QueryInfo.create(blIndex), hitsArrays, null);
        getHitOrFragmentInfo(ds, hits, hitsArrays.get(0), wordsAroundHit, origContent, !isHit, null, new HashSet<>(this.getAnnotationsToWrite()));
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
     * @param annotationsTolist what annotations to include
     */
    public static void getHitOrFragmentInfo(DataStream ds, Hits hits, Hit hit, ContextSize wordsAroundHit,
            boolean useOrigContent, boolean isFragment, String docPid, Set<Annotation> annotationsTolist) {
        ds.startMap();
        if (docPid != null) {
            // Add basic hit info
            ds.entry("docPid", docPid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());
        }

        Hits singleHit = hits.window(hit);
        if (useOrigContent) {
            Concordances concordances = singleHit.concordances(wordsAroundHit, ConcordanceType.CONTENT_STORE);
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
                ds.startEntry("left").contextList(c.annotations(), annotationsTolist, c.left()).endEntry()
                        .startEntry("match").contextList(c.annotations(), annotationsTolist, c.match()).endEntry()
                        .startEntry("right").contextList(c.annotations(), annotationsTolist, c.right()).endEntry();
            } else {
                ds.contextList(c.annotations(), annotationsTolist, c.tokens());
            }
        }
        ds.endMap();
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
