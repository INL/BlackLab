package nl.inl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.BOMInputStream;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public class FileReferenceFile implements FileReference {

    /** The file */
    private File file;

    /** The encoding, or null if BOM not yet detected */
    private Charset charSet;

    FileReferenceFile(File file) {
        this.file = file;
    }

    @Override
    public String getPath() {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public FileReference withBytes() {
        try {
            return FileReference.fromBytes(getPath(), FileUtils.readFileToByteArray(file), file);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public FileReference withCreateInputStream() {
        return this;
    }

    @Override
    public byte[] getBytes() {
        throw new UnsupportedOperationException("Bytes not available; call withBytes() on the FileReference first");
    }

    @Override
    public InputStream getSinglePassInputStream() {
        return createInputStream();
    }

    @Override
    public InputStream createInputStream() {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public File getAssociatedFile() {
        return file;
    }

    @Override
    public Charset getCharSet() {
        if (charSet == null) {
            // Check the file for a BOM to determine the encoding
            try (BOMInputStream is = UnicodeStream.wrap(createInputStream())) {
                charSet = UnicodeStream.getCharset(is);
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
        }
        return charSet;
    }
}
