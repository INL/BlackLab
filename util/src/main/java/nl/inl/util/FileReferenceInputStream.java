package nl.inl.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public class FileReferenceInputStream implements FileReference {

    String path;

    BOMInputStream is;

    File file;

    FileReferenceInputStream(String path, InputStream is, File file) {
        this.path = path;
        this.is = UnicodeStream.wrap(is);
        this.file = file;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public FileReference withBytes() {
        // NOTE: This only works if you haven't read from the InputStream yet!
        try {
            return FileReference.fromBytes(path, IOUtils.toByteArray(is), file);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes() {
        throw new UnsupportedOperationException("Bytes not available; call withBytes() on the FileReference first");
    }

    @Override
    public InputStream getSinglePassInputStream() {
        return is;
    }

    @Override
    public InputStream createInputStream() {
        throw new UnsupportedOperationException("Cannot create input stream; call withCreateInputStream() on the FileReference first");
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
        return UnicodeStream.getCharset(is);
    }
}
