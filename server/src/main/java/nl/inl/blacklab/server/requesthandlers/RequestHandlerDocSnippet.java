package nl.inl.blacklab.server.requesthandlers;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceOperations;
import nl.inl.blacklab.server.util.BlsUtils;

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
        String docId = i >= 0 ? urlPathInfo.substring(0, i) : urlPathInfo;
        if (docId.length() == 0)
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");

        BlackLabIndex blIndex = blIndex();
        int luceneDocId = BlsUtils.getDocIdFromPid(blIndex, docId);
        if (luceneDocId < 0)
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
        Document document = blIndex.luceneDoc(luceneDocId);
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docId + "'.", "INTERR_FETCHING_DOCUMENT_SNIPPET");

        ContextSize wordsAroundHit;
        int start, end;
        boolean isHit = false;
        Optional<Integer> hitStart = params.getHitStart();
        if (hitStart.isPresent()) {
            start = hitStart.get();
            end = params.getHitEnd();
            wordsAroundHit = ContextSize.get(params.getWordsAroundHit());
            isHit = true;
        } else {
            start = params.getWordStart();
            end = params.getWordEnd();
            wordsAroundHit = ContextSize.hitOnly();
        }

        if (start < 0 || end < 0 || wordsAroundHit.left() < 0 || wordsAroundHit.right() < 0 || start > end) {
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        }

        // Clamp snippet to max size
        int snippetStart = Math.max(0, start - wordsAroundHit.left());
        int snippetEnd = end + wordsAroundHit.right();
        int maxContextSize = searchMan.config().getParameters().getContextSize().getMaxInt();
        if (snippetEnd - snippetStart > maxContextSize) {
            int clampedWindow = Math.max(0, (maxContextSize - (end - start)) / 2);
            snippetStart = Math.max(0, start - clampedWindow);
            snippetEnd = end + clampedWindow;
//			throw new BadRequest("SNIPPET_TOO_LARGE", "Snippet too large. Maximum size for a snippet is " + searchMan.config().maxSnippetSize() + " words.");
        }
        boolean origContent = params.getConcordanceType() == ConcordanceType.CONTENT_STORE;
        Hits hits = Hits.singleton(QueryInfo.create(blIndex), luceneDocId, start, end);
        DataStreamUtil.hitOrFragmentInfo(ds, hits, hits.get(0), wordsAroundHit, origContent, !isHit, null,
                WebserviceOperations.getAnnotationsToWrite(blIndex, params));
        return HTTP_OK;
    }

    @Override
    protected boolean isDocsOperation() {
        return true;
    }

}
