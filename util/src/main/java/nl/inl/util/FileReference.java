package nl.inl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

/** Represents a file to be indexed.
 *
 * May be in the form of an input stream, byte array, or file.
 */
public interface FileReference {

    static FileReference fromBytes(String path, byte[] contents, File assocFile) {
        return new FileReferenceBytes(path, contents, assocFile);
    }

    static FileReference fromBytesOverrideCharset(String path, byte[] contents, Charset charset) {
        return new FileReferenceBytes(path, contents, charset);
    }

    static FileReference fromFile(File file) {
        return new FileReferenceFile(file);
    }

    static FileReference fromInputStream(String path, InputStream is, File assocFile) {
        return new FileReferenceInputStream(path, is, assocFile);
    }

    static FileReference fromCharArray(String path, char[] charArray, File assocFile) {
        return new FileReferenceChars(path, charArray, assocFile);
    }

    /**
     * Path to the file (containing archive may be included).
     */
    String getPath();

    /** Return a file reference where createInputStream() and createReader() work,
     *  so we can process the file multiple times. */
    FileReference withCreateReader();

    FileReference withGetTextContent();

    default FileReference inMemoryIfSmallerThan(int fileSizeInBytes) {
        return this;
    }

    /**
     * Get contents as a byte array.
     */
    byte[] getBytes();

    /**
     * Get an input stream to the file contents.
     * Call this if you only need to process the file ONCE.
     * @return input stream
     */
    default InputStream getSinglePassInputStream() {
        return createInputStream();
    }

    /**
     * Get an input stream to the file contents.
     * May be called multiple times.
     * @return input stream
     */
    default InputStream createInputStream() {
        throw new UnsupportedOperationException("Cannot create input stream; call withMultiStream() on the FileReference first");
    }

    /**
     * Get a reader to the file contents.
     * May be called multiple times.
     * @return reader
     */
    default BufferedReader createReader() {
        return createReader(null);
    }

    default BufferedReader createReader(Charset overrideEncoding) {
        if (overrideEncoding == null)
            overrideEncoding = getCharSet();
        return new BufferedReader(new InputStreamReader(createInputStream(), overrideEncoding));
    }

    /** Is efficient getTextContent(start, end) supported? */
    default boolean hasGetTextContent() {
        return false;
    }

    /**
     * Get part of the document.
     * @param startOffset the offset to start reading at
     * @param endOffset the offset to stop reading at, or -1 to read until the end
     * @return the content read
     */
    default TextContent getTextContent(long startOffset, long endOffset) {
        // We could do this using a Reader, but better to leave managing that to the caller,
        // which knows if it needs multiple parts of the file and can make sure to minimize
        // passes over the file.
        throw new UnsupportedOperationException("Cannot get text content; call withCharArray() on the FileReference first");
//        try (BufferedReader reader = createReader()) {
//            if (startOffset > 0)
//                reader.skip(startOffset);
//            if (endOffset != -1) {
//                int length = (int)(endOffset - startOffset);
//                char[] result = new char[length];
//                if (reader.read(result, 0, length) < 0)
//                    throw new RuntimeException("Unexpected end of file");
//                return TextContent.from(result);
//            } else {
//                return TextContent.from(FileUtils.readFileToString(getFile(), getCharSet()));
//            }
//        } catch (IOException e) {
//            throw new BlackLabRuntimeException(e);
//        }
    }

    /**
     * This file, or null if this is not a (simple) file.
     */
    default File getFile() {
        return null;
    }

    /**
     * The corresponding file or archive this content is from, or null if unknown.
     */
    File getAssociatedFile();

    /** Detected or configured charset to use for file (or just the default) */
    Charset getCharSet();
}
