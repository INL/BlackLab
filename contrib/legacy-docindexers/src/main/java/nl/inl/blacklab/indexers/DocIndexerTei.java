package nl.inl.blacklab.indexers;

import java.io.Reader;

import nl.inl.blacklab.index.DocWriter;

/**
 * Index a TEI P4/P5 file.
 *
 * For information about TEI, see http://www.tei-c.org/
 *
 * Note: this class assumes that PoS information is in the "type" attribute. For
 * data that has the PoS information in the "function" attribute, use
 * DocIndexerTeiPosInFunctionAttr (or set the appropriate configuration
 * parameters).
 */
public class DocIndexerTei extends DocIndexerTeiBase {

    public static String getDisplayName() {
        return "TEI-DocIndexer (alternate TEI indexer)";
    }

    public static String getDescription() {
        return "Main contents should be in body element, should be tokenized and PoS tags should be in the type attribute.";
    }

    public DocIndexerTei(DocWriter indexer, String fileName, Reader reader, String contentElement) {
        super(indexer, fileName, reader, contentElement, true);
    }

    public DocIndexerTei(DocWriter indexer, String fileName, Reader reader) {
        this(indexer, fileName, reader, "body");
    }

}
