package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** A way to access the contents of a document from memory or, for large documents, from disk. */
public class DocumentReference {

    /** If doc is larger than this, save it to a temporary file and read it back later. */
    private static final int MAX_DOC_SIZE_IN_MEMORY_BYTES = 4_096_000;

    /**
     * The document as a string, will be used for storing document and position calculation.
     * (for large document, may be null after init)
     */
    private char[] contents;

    /**
     * If we were called with a file, we'll store it here.
     * Large files also get temporarily stored on disk until they're needed again.
     */
    private File file;

    /**
     * If we created a temporary file, we'll delete it on exit.
     */
    private boolean deleteFileOnExit = false;

    public DocumentReference(char[] contents, File file) {
        this.contents = contents;
        this.file = file;
        swapIfTooLarge();
    }

    public void swapIfTooLarge() {
        if (contents != null && contents.length * Character.BYTES > MAX_DOC_SIZE_IN_MEMORY_BYTES) {
            if (file == null) {
                // We don't have a file with the contenst yet; create it now.
                try {
                    file = File.createTempFile("blDocToIndex", null);
                    file.deleteOnExit();
                    deleteFileOnExit = true;
                    try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                        IOUtils.write(contents, writer);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error swapping large doc to disk", e);
                }
            }
            contents = null; // drop the contents from memory
        }
    }

    public String get() {
        try {
            if (contents == null)
                return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BlackLabRuntimeException("unable to read document cache from disk");
        }
        return new String(contents);
    }

    public void clean() {
        if (file != null && deleteFileOnExit)
            file.delete();
        contents = null;
        file = null;
    }
}
