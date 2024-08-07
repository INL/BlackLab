package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.function.Supplier;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** A way to access the contents of a document.
 *
 * Contents may be stored in memory for smaller documents, or be read from disk for larger ones.
 */
public class DocumentReferenceFile extends DocumentReferenceAbstract {

    /** Charset used by the file */
    private Charset fileCharset;

    /**
     * If we were called with a file, we'll store it here.
     * Large files also get temporarily stored on disk until they're needed again.
     */
    private File file;

    /**
     * If we created a temporary file, we'll delete it on exit.
     */
    private boolean deleteFileOnExit;

    DocumentReferenceFile(File file, Charset fileCharset, boolean deleteOnExit) {
        this.fileCharset = fileCharset;
        this.file = file;
        this.deleteFileOnExit = deleteOnExit;
        if (deleteFileOnExit)
            file.deleteOnExit();
    }

    @Override
    public void clean() {
        if (deleteFileOnExit && file != null)
            file.delete();
        file = null;
        super.clean();
    }

    @Override
    public Supplier<Reader> getBaseDocReaderSupplier() {
        return () -> {
            try {
                return new FileReader(file, fileCharset);
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
        };
    }
}
