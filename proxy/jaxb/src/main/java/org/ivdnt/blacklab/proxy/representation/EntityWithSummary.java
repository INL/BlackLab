package org.ivdnt.blacklab.proxy.representation;

public interface EntityWithSummary {

    /** Remove summary.pattern so we don't try to serialize it to XML (which causes headaches). */
    public SearchSummary getSummary();
}
