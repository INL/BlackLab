package nl.inl.blacklab.indexers.config.saxon;

import java.io.Reader;

interface XIncludeResolver {

    Reader getDocumentReader();

    boolean anyXIncludesFound();
}
