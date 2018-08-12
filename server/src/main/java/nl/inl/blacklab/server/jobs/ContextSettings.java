package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.results.ContextSize;

public class ContextSettings {

    private ContextSize size;

    private ConcordanceType concType;

    public ContextSettings(ContextSize size, ConcordanceType concType) {
        super();
        this.size = size;
        this.concType = concType;
    }

    public ContextSize size() {
        return size;
    }

    public ConcordanceType concType() {
        return concType;
    }

    @Override
    public String toString() {
        return "ctxsize=" + size + ", conctype=" + concType;
    }

}
