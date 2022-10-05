package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocResults;

public class ResultSummaryNumDocs {
    private boolean isViewDocGroup;
    private DocResults docResults;
    private boolean countFailed;
    private CorpusSize subcorpusSize;

    ResultSummaryNumDocs(boolean isViewDocGroup, DocResults docResults, boolean countFailed,
            CorpusSize subcorpusSize) {
        this.isViewDocGroup = isViewDocGroup;
        this.docResults = docResults;
        this.countFailed = countFailed;
        this.subcorpusSize = subcorpusSize;
    }

    public boolean isViewDocGroup() {
        return isViewDocGroup;
    }

    public DocResults getDocResults() {
        return docResults;
    }

    public boolean isCountFailed() {
        return countFailed;
    }

    public CorpusSize getSubcorpusSize() {
        return subcorpusSize;
    }
}
