package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.ResultsStats;

public class ResultSummaryNumHits {
    private ResultsStats hitsStats;
    private ResultsStats docsStats;
    private boolean waitForTotal;
    private boolean countFailed;
    private CorpusSize subcorpusSize;

    ResultSummaryNumHits(ResultsStats hitsStats, ResultsStats docsStats,
            boolean waitForTotal,
            boolean countFailed, CorpusSize subcorpusSize) {
        this.hitsStats = hitsStats;
        this.docsStats = docsStats;
        this.waitForTotal = waitForTotal;
        this.countFailed = countFailed;
        this.subcorpusSize = subcorpusSize;
    }

    public ResultsStats getHitsStats() {
        return hitsStats;
    }

    public ResultsStats getDocsStats() {
        return docsStats;
    }

    public boolean isWaitForTotal() {
        return waitForTotal;
    }

    public boolean isCountFailed() {
        return countFailed;
    }

    public CorpusSize getSubcorpusSize() {
        return subcorpusSize;
    }
}
