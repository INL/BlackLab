package nl.inl.blacklab.server.requesthandlers;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ResultDocSnippet;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/**
 * Get a snippet of a document's contents.
 */
public class RequestHandlerDocSnippet extends RequestHandler {
    public RequestHandlerDocSnippet(BlackLabServer servlet, HttpServletRequest request, User user, String indexName,
            String urlResource, String urlPathPart) {
        super(servlet, request, user, indexName, urlResource, urlPathPart);
    }

    @Override
    public int handle(DataStream ds) throws BlsException {
        int i = urlPathInfo.indexOf('/');
        String docPid = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (docPid.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");
        params.setDocPid(docPid);

        ResultDocSnippet result = WebserviceOperations.docSnippet(params, searchMan);
        dstreamHitOrFragmentInfo(ds, result);
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

    /**
     * Output a hit (or just a document fragment with no hit in it)
     *
     * @param ds output stream
     * @param result hit to output
     */
    private static void dstreamHitOrFragmentInfo(DataStream ds, ResultDocSnippet result) {

        Hits hits = result.getHits();
        Hit hit = hits.get(0);
        ContextSize wordsAroundHit = result.getWordsAroundHit();
        boolean useOrigContent = result.isOrigContent();
        boolean isFragment = !result.isHit();
        String docPid = null; // (not sure why this is always null..?) result.getParams().getDocPid();
        List<Annotation> annotationsToList = result.getAnnotsToWrite();

        // TODO: can we merge this with hit()...?
        ds.startMap();
        if (docPid != null) {  // always false, see above? weird!
            // Add basic hit info
            ds.entry("docPid", docPid);
            ds.entry("start", hit.start());
            ds.entry("end", hit.end());
        }

        Hits singleHit = hits.window(hit);
        if (useOrigContent) {
            // We're using original content.
            Concordances concordances = singleHit.concordances(wordsAroundHit, ConcordanceType.CONTENT_STORE);
            Concordance c = concordances.get(hit);
            if (!isFragment) {
                ds.startEntry("left").xmlFragment(c.left()).endEntry()
                        .startEntry("match").xmlFragment(c.match()).endEntry()
                        .startEntry("right").xmlFragment(c.right()).endEntry();
            } else {
                ds.xmlFragment(c.match());
            }
        } else {
            Kwics kwics = singleHit.kwics(wordsAroundHit);
            Kwic c = kwics.get(hit);
            if (!isFragment) {
                ds.startEntry("left").contextList(c.annotations(), annotationsToList, c.left()).endEntry()
                        .startEntry("match").contextList(c.annotations(), annotationsToList, c.match()).endEntry()
                        .startEntry("right").contextList(c.annotations(), annotationsToList, c.right()).endEntry();
            } else {
                ds.startEntry("snippet").contextList(c.annotations(), annotationsToList, c.tokens()).endEntry();
            }
        }
        ds.endMap();
    }

}
