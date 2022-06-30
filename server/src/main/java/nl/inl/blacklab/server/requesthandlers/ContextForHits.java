package nl.inl.blacklab.server.requesthandlers;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Kwic;
import nl.inl.blacklab.search.results.Concordances;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Kwics;

public class ContextForHits {

    public static ContextForHits kwics(Kwics kwics) {
        return new ContextForHits(kwics, null);
    }

    public static ContextForHits concordances(Concordances concordances) {
        return new ContextForHits(null, concordances);
    }

    public static ContextForHits get(Hits hits, ConcordanceType concordanceType, ContextSize contextSize) {
        ContextForHits contextForHits;
        if (concordanceType == ConcordanceType.CONTENT_STORE)
            contextForHits = ContextForHits.concordances(
                    hits.concordances(contextSize, ConcordanceType.CONTENT_STORE));
        else
            contextForHits = ContextForHits.kwics(hits.kwics(contextSize));
        return contextForHits;
    }

    private Concordances concordances = null;

    private Kwics kwics = null;

    private ContextForHits(Kwics kwics, Concordances concordances) {
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
