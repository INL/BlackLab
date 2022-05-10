package nl.inl.blacklab.indexers;

import java.io.Reader;

import nl.inl.blacklab.index.DocWriter;

/**
 * Index a TEI P4/P5 file, using the &lt;text&gt; element instead of the
 * &lt;body&gt; element as the main content element.
 *
 * For information about TEI, see http://www.tei-c.org/
 */
public class DocIndexerTeiText extends DocIndexerTeiBase {

    public static String getDisplayName() {
        return "TEI-DocIndexer-text (alternate TEI indexer)";
    }

    public static String getDescription() {
        return "Main contents should be in text element, should be tokenized and PoS tags should be in the type attribute.";
    }

    public DocIndexerTeiText(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader, "text", true);
    }
}
