package nl.inl.blacklab.indexers.config.saxon;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
            return DocumentReferenceFile.fromCharArray(contents);
        }
        return new DocumentReferenceCharArray(contents);
    }

    static DocumentReference fromFile(File file, Charset fileCharset) {
        if (fileCharset == null)
            fileCharset = StandardCharsets.UTF_8;
        return new DocumentReferenceFile(file, fileCharset, false);
    }

    static DocumentReference fromByteArray(byte[] contents, Charset defaultCharset) {
        assert contents != null;
        if (defaultCharset == null)
            defaultCharset = StandardCharsets.UTF_8;
        try {
            char[] charContents = IOUtils.toCharArray(new ByteArrayInputStream(contents), defaultCharset);
            return fromCharArray(charContents);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static DocumentReference fromReaderProvider(Supplier<Reader> readerProvider) {
        return new DocumentReferenceReaderProvider(readerProvider);
    }

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

    static DocumentReference fromReader(Reader reader) {
        try {
            return fromCharArray(IOUtils.toCharArray(reader));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Read characters from a Reader, starting at startOffset and ending at endOffset.
     *
     * @param reader the Reader to read from
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the characters read
     * @throws IOException
     */
    static char[] readerToCharArray(Reader reader, long startOffset, long endOffset) {
        try {
            if (startOffset > 0)
                IOUtils.skip(reader, startOffset);
            if (endOffset != -1) {
                int length = (int)(endOffset - startOffset);
                char[] result = new char[length];
                if (reader.read(result, 0, length) < 0)
                    throw new RuntimeException("Unexpected end of file");
                return result;
            } else {
                return IOUtils.toCharArray(reader);
            }
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    void setXIncludeDirectory(File currentXIncludeDir);

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
