package nl.inl.blacklab.indexers.config.saxon;

import java.io.Reader;
import java.util.function.Supplier;

import nl.inl.util.FileReference;
import nl.inl.util.TextContent;

/** A way to access the contents of an XML document via a FileReference.
 *
 * Reads small files into memory for efficiency.
 */
public class DocumentReferenceFileReference extends DocumentReferenceAbstract {

    /** Files smaller than this may be read into memory. */
    public static final int FILE_IN_MEMORY_THRESHOLD_BYTES = 4_000_000;

    /**
     * If we were called with a file, we'll store it here.
     * Large files also get temporarily stored on disk until they're needed again.
     */
    private FileReference file;

    DocumentReferenceFileReference(FileReference file) {
        // Make sure to read small files into memory,
        // and that we can create multiple readers (needed for getTextContent later).
        this.file = file
                .inMemoryIfSmallerThan(FILE_IN_MEMORY_THRESHOLD_BYTES)
                .withCreateReader();
    }

    @Override
    public void clean() {
        file = null;
        super.clean();
    }

    @Override
    public Supplier<Reader> getBaseDocReaderSupplier() {
        return () -> file.createReader();
    }

    @Override
    public TextContent getTextContent(long startOffset, long endOffset) {
        if (file.hasGetTextContent()) {
            // File is char array based, so it can do this efficiently.
            return file.getTextContent(startOffset, endOffset);
        }
        // We can only access the file through a reader. Use our own implementation that makes we can
        // use the same one for multiple reads.
        return super.getTextContent(startOffset, endOffset);
    }
}
