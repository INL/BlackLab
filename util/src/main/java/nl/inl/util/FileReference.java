package nl.inl.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public interface FileReference {

    static FileReference fromBytes(String path, byte[] contents, File file) {
        return new FileReferenceBytes(path, contents, file);
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
        try {
            return new FileReferenceBytes(path, IOUtils.toByteArray(is), file);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
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
     */
    InputStream getSinglePassInputStream();

    /**
     * Get an input stream to the file contents.
     * May be called multiple times.
     */
    InputStream createInputStream();

    /**
     * This file, or null if this is not a (simple) file.
     */
    File getFile();

    /**
     * The corresponding file or archive this content is from, or null if unknown.
     */
    File getAssociatedFile();
}
