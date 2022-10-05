package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.SearchCreator;

public class ResultListOfHits {
    private SearchCreator params;
    private Hits hits;
    private ConcordanceContext concordanceContext;
    private Map<Integer, String> docIdToPid;

    ResultListOfHits(SearchCreator params, Hits hits, ConcordanceContext concordanceContext,
            Map<Integer, String> docIdToPid) {
        this.params = params;
        this.hits = hits;
        this.concordanceContext = concordanceContext;
        this.docIdToPid = docIdToPid;
    }

    public SearchCreator getParams() {
        return params;
    }

    public Hits getHits() {
        return hits;
    }

    public ConcordanceContext getConcordanceContext() {
        return concordanceContext;
    }

    public Map<Integer, String> getDocIdToPid() {
        return docIdToPid;
    }
}
