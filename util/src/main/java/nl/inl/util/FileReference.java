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

    /** Return a file reference where createReader() works,
     *  so we can process the file multiple times (e.g. parse XML, get document contents to store). */
    FileReference withCreateReader();

    /**
     * Return a file reference where getTextContent() works,
     * so we can get parts of the file efficiently (e.g. get document contents to store).
     * @return
     */
    FileReference withGetTextContent();

    /**
     * If we know this is a small file, read in into memory.
     *
     * @param fileSizeInBytes threshold for reading into memory
     * @return this or a new FileReference
     */
    default FileReference inMemoryIfSmallerThan(int fileSizeInBytes) {
        return this;
    }

    /**
     * Get contents as a byte array.
     *
     * (only used by the older VTD-XML indexer)
     */
    byte[] getBytes();

    /**
     * Get an input stream to the file contents.
     * Call this if you only need to process the file ONCE.
     * Supported by all implementations.
     *
     * @return input stream
     */
    InputStream getSinglePassInputStream();

    /**
     * Get a reader to the file contents.
     * Call this if you only need to process the file ONCE.
     * Supported by all implementations.
     *
     * @return reader
     */
    default BufferedReader getSinglePassReader() {
        return new BufferedReader(new InputStreamReader(getSinglePassInputStream()));
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
        throw new UnsupportedOperationException("Cannot create reader; call withCreateReader() first");
    }

    /** Is getTextContent(start, end) supported?
     *
     * Only supported for implementations that can do it efficiently (i.e. with random access).
     */
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
