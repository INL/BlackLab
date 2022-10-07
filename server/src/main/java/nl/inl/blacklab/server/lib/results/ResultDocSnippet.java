package nl.inl.blacklab.server.lib.results;

import java.util.List;
import java.util.Optional;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.util.BlsUtils;

public class ResultDocSnippet {

    private final SearchCreator params;

    private Hits hits;

    private boolean isHit;

    private final ContextSize wordsAroundHit;

    private final boolean origContent;

    private final List<Annotation> annotsToWrite;

    ResultDocSnippet(SearchCreator params, SearchManager searchMan) {
        this.params = params;

        BlackLabIndex index = params.blIndex();
        String docPid = params.getDocPid();
        int luceneDocId = BlsUtils.getDocIdFromPid(index, docPid);
        if (luceneDocId < 0)
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
        Document document = index.luceneDoc(luceneDocId);
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.",
                    "INTERR_FETCHING_DOCUMENT_SNIPPET");

        int start, end;
        isHit = false;
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
        origContent = params.getConcordanceType() == ConcordanceType.CONTENT_STORE;
        hits = Hits.singleton(QueryInfo.create(index), luceneDocId, start, end);

        annotsToWrite = WebserviceOperations.getAnnotationsToWrite(params);
    }

    public SearchCreator getParams() {
        return params;
    }

    public Hits getHits() {
        return hits;
    }

    public boolean isHit() {
        return isHit;
    }

    public ContextSize getWordsAroundHit() {
        return wordsAroundHit;
    }

    public boolean isOrigContent() {
        return origContent;
    }

    public List<Annotation> getAnnotsToWrite() {
        return annotsToWrite;
    }
}
