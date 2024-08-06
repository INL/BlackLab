package nl.inl.blacklab.indexers.config.saxon;

import java.io.CharArrayReader;
import java.io.Reader;

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

    char[] getCharArray(long startOffset, long endOffset) {
        if (startOffset == 0 && endOffset == -1 || endOffset == contents.length)
            return contents;
        long length = (endOffset < 0 ? contents.length : endOffset) - startOffset;
        char[] result = new char[(int)length];
        System.arraycopy(result, (int)startOffset, result, 0, (int)length);
        return result;
    }

    @Override
    public void clean() {
        contents = null;
        super.clean();
    }

    @Override
    char[] getBaseDocument() {
        return contents;
    }

    XIncludeResolver getDummyXIncludeResolver() {
        return new XIncludeResolver() {
            @Override
            public Reader getDocumentReader() {
                return new CharArrayReader(contents);
            }

            @Override
            public boolean anyXIncludesFound() {
                return false;
            }
        };
    }
}
