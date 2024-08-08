package nl.inl.blacklab.indexers.config.saxon;

import java.io.CharArrayReader;
import java.io.Reader;
import java.util.function.Supplier;

import nl.inl.blacklab.contentstore.TextContent;

/** A way to access the contents of a document.
 *
 * Contents may be stored in memory for smaller documents, or be read from disk for larger ones.
 */
public class DocumentReferenceCharArray extends DocumentReferenceAbstract {

    /** The document contents */
    private char[] contents;

    DocumentReferenceCharArray(char[] contents) {
        this.contents = contents;
    }

    @Override
    public Supplier<Reader> getBaseDocReaderSupplier() {
        return () -> new CharArrayReader(contents);
    }

    @Override
    public TextContent getTextContent(long startOffset, long endOffset) {
        return new TextContent(contents, (int)startOffset, (int)(endOffset - startOffset));
    }

    @Override
    public void clean() {
        contents = null;
        super.clean();
    }
}
