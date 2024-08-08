package nl.inl.blacklab.indexers.config.saxon;

import java.io.Reader;
import java.util.function.Supplier;

import nl.inl.util.FileReference;

/** A way to access the contents of an XML document via a FileReference.
 *
 * Reads small files into memory for efficiency.
 */
public class DocumentReferenceFileReference extends DocumentReferenceAbstract {

    /** Files smaller than this will be read into memory. */
    public static final int FILE_IN_MEMORY_THRESHOLD = 4_000_000;

    /**
     * If we were called with a file, we'll store it here.
     * Large files also get temporarily stored on disk until they're needed again.
     */
    private FileReference file;

    DocumentReferenceFileReference(FileReference file) {
        if (file.getFile() != null && file.getFile().length() < FILE_IN_MEMORY_THRESHOLD) {
            // Fairly small; read the file into memory for efficiency
            this.file = file.withBytes();
        } else {
            // Just make sure we can make multiple passes over the file if necessary
            this.file = file.withCreateInputStream();
        }
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
}
