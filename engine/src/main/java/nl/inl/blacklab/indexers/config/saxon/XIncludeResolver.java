package nl.inl.blacklab.indexers.config.saxon;

import java.io.Reader;

public interface XIncludeResolver {
    DocumentReference getDocumentReference();

    Reader getDocumentReader();

    CharPositionsTracker getCharPositionsTracker();

    boolean anyXIncludesFound();
}
