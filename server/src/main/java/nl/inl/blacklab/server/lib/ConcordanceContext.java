package nl.inl.blacklab.server.lib;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;

public class ConcordanceContext {

    public static ConcordanceContext kwics(Kwics kwics) {
        return new ConcordanceContext(kwics, null);
    }

    public static ConcordanceContext concordances(Concordances concordances) {
        return new ConcordanceContext(null, concordances);
    }

    public static ConcordanceContext get(Hits hits, ConcordanceType concordanceType, ContextSize contextSize) {
        ConcordanceContext concordanceContext;
        if (concordanceType == ConcordanceType.CONTENT_STORE)
            concordanceContext = ConcordanceContext.concordances(
                    hits.concordances(contextSize, ConcordanceType.CONTENT_STORE));
        else
            concordanceContext = ConcordanceContext.kwics(hits.kwics(contextSize));
        return concordanceContext;
    }

    private Concordances concordances = null;

    private Kwics kwics = null;

    private ConcordanceContext(Kwics kwics, Concordances concordances) {
        this.kwics = kwics;
        this.concordances = concordances;
    }

    public boolean isConcordances() {
        return concordances != null;
    }

    public Concordance getConcordance(Hit hit) {
        return concordances.get(hit);
    }

    public Kwic getKwic(Hit hit) {
        return kwics.get(hit);
    }
}
