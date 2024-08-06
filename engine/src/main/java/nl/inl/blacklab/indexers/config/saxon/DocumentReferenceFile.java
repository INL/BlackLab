package nl.inl.blacklab.indexers.config.saxon;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** A way to access the contents of a document.
 *
 * Contents may be stored in memory for smaller documents, or be read from disk for larger ones.
 */
public class DocumentReferenceFile extends DocumentReferenceAbstract {

    static DocumentReferenceFile fromCharArray(char[] contents) {
        try {
            File file = File.createTempFile("blDocToIndex", null);
            file.deleteOnExit();
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                IOUtils.write(contents, writer);
            }
            return new DocumentReferenceFile(file, StandardCharsets.UTF_8, true);
        } catch (IOException e) {
            throw new RuntimeException("Error swapping large doc to disk", e);
        }
    }

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
    }

    @Override
    public void clean() {
        if (deleteFileOnExit && file != null)
            file.delete();
        file = null;
        super.clean();
    }

    char[] getBaseDocument() {
        try {
            return IOUtils.toCharArray(new FileReader(file, fileCharset));
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    XIncludeResolver getDummyXIncludeResolver() {
        return new XIncludeResolver() {
            @Override
            public Reader getDocumentReader() {
                try {
                    return new FileReader(file, fileCharset);
                } catch (IOException e) {
                    throw new BlackLabRuntimeException(e);
                }
            }

            @Override
            public boolean anyXIncludesFound() {
                return false;
            }
        };
    }
}
