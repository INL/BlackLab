package nl.inl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** Represents a file to be indexed.
 *
 * May be in the form of an input stream, byte array, or file.
 */
public interface FileReference {

    static FileReference fromBytes(String path, byte[] contents, File file) {
        return new FileReferenceBytes(path, contents, file);
    }

    static FileReference fromBytesOverrideCharset(String path, byte[] contents, Charset charset) {
        return new FileReferenceBytes(path, contents, charset);
    }

    static FileReference fromFile(File file) {
        if (file.length() > Constants.JAVA_MAX_ARRAY_SIZE) {
            // Too large to read into a byte array. Use input stream.
            //FileInputStream inputStream = FileUtils.openInputStream(file);
            //processInputStream(file.getAbsolutePath(), inputStream, file);
            return new FileReferenceFile(file);
        } else {
            // Read entire file into byte array (more efficient).
            try {
                return new FileReferenceBytes(file.getAbsolutePath(), FileUtils.readFileToByteArray(file), file);
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
        }
    }

    static FileReference fromInputStream(String path, InputStream is, File file) {
        return new FileReferenceInputStream(path, is, file);
    }

    /**
     * Path to the file (containing archive may be included).
     */
    String getPath();

    /** Return a file reference that has a byte array with the file contents. */
    FileReference withBytes();

    /** Return a file reference where createInputStream() works,
     *  so we can process the file multiple times. */
    default FileReference withCreateInputStream() {
        return withBytes();
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
    InputStream getSinglePassInputStream();

    /**
     * Get an input stream to the file contents.
     * May be called multiple times.
     * @return input stream
     */
    InputStream createInputStream();

    /**
     * Get a reader to the file contents.
     * May be called multiple times.
     * @return reader
     */
    default BufferedReader createReader() {
        return new BufferedReader(new InputStreamReader(createInputStream(), getCharSet()));
    }

    default BufferedReader createReader(Charset overrideEncoding) {
        if (overrideEncoding == null)
            overrideEncoding = getCharSet();
        return new BufferedReader(new InputStreamReader(createInputStream(), overrideEncoding));
    }

    /**
     * This file, or null if this is not a (simple) file.
     */
    File getFile();

    /**
     * The corresponding file or archive this content is from, or null if unknown.
     */
    File getAssociatedFile();

    /** Detected or configured charset to use for file (or just the default) */
    Charset getCharSet();
}
