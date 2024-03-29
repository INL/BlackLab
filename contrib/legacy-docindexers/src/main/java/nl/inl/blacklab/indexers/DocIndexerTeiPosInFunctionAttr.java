package nl.inl.blacklab.indexers;

import java.io.Reader;

import nl.inl.blacklab.index.DocWriter;

/**
 * Index a TEI P4/P5 file with the PoS in the "function" attribute.
 *
 * For information about TEI, see http://www.tei-c.org/
 */
public class DocIndexerTeiPosInFunctionAttr extends DocIndexerTeiBase {

    public static String getDisplayName() {
        return "TEI-DocIndexer-function (alternate TEI indexer)";
    }

    public static String getDescription() {
        return "Main contents should be in body element, should be tokenized and PoS tags should be in the function attribute.";
    }

    public DocIndexerTeiPosInFunctionAttr(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader, "body", false);
    }
}
