package nl.inl.blacklab.indexers.config.saxon;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.contentstore.TextContent;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public interface DocumentReference {

    /** If doc is larger than this, save it to a temporary file and read it back later. */
    int MAX_DOC_SIZE_IN_MEMORY_BYTES = 4_096_000;

    static DocumentReference fromCharArray(char[] contents) {
        if (contents.length * Character.BYTES > MAX_DOC_SIZE_IN_MEMORY_BYTES) {
            // Swap to file
            try {
                File file = File.createTempFile("blDocToIndex", null);
                try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                    IOUtils.write(contents, writer);
                    return new DocumentReferenceFile(file, StandardCharsets.UTF_8, true);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error swapping large doc to disk", e);
            }
        }
        return new DocumentReferenceCharArray(contents);
    }

    static DocumentReference fromFile(File file, Charset fileCharset) {
        if (fileCharset == null)
            fileCharset = StandardCharsets.UTF_8;
        // Read small files into memory to avoid having to read from disk multiple times?
        if (file.length() < MAX_DOC_SIZE_IN_MEMORY_BYTES) {
            // Small file. More efficient to read in into memory first?
            try {
                char[] contents = IOUtils.toCharArray(new FileReader(file, fileCharset));
                return new DocumentReferenceCharArray(contents);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
        return new DocumentReferenceFile(file, fileCharset, false);
    }

    static DocumentReference fromByteArray(byte[] contents, Charset defaultCharset) {
        assert contents != null;
        if (defaultCharset == null)
            defaultCharset = StandardCharsets.UTF_8;
        if (contents.length * Byte.BYTES > MAX_DOC_SIZE_IN_MEMORY_BYTES) {
            // Swap to file
            try {
                File file = File.createTempFile("blDocToIndex", null);
                try (OutputStream os = new FileOutputStream(file)) {
                    IOUtils.write(contents, os);
                    return new DocumentReferenceFile(file, defaultCharset, true);
                }
            } catch (IOException e) {
                throw new RuntimeException("Error swapping large doc to disk", e);
            }
        }

        // Use DocumentReferenceReaderSupplier to avoid converting to char array
        Charset cs = defaultCharset;
        return fromReaderSupplier(() -> new InputStreamReader(new ByteArrayInputStream(contents), cs));
    }

    static DocumentReference fromReaderSupplier(Supplier<Reader> supplier) {
        return new DocumentReferenceReaderSupplier(supplier);
    }

    // Avoid this as we have to convert to an array
    static DocumentReference fromInputStream(InputStream is, Charset defaultCharset) {
        try {
            try {
                char[] contents = IOUtils.toCharArray(is, defaultCharset);
                return fromCharArray(contents);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    // Avoid this as we have to convert to an array
    static DocumentReference fromReader(Reader reader) {
        try {
            return fromCharArray(IOUtils.toCharArray(reader));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    void setXIncludeDirectory(File dir);

    /**
     * Get the document as a Reader. May be called multiple times.
     * @return the reader
     */
    Reader getDocumentReader();

    /**
     * Get part of the document as a TextContent object.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    TextContent getTextContent(long startOffset, long endOffset);

    /**
     * Clean up resources.
     */
    void clean();
}
