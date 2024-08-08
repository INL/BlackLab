package nl.inl.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public class FileReferenceInputStream implements FileReference {

    String path;

    UnicodeStream is;

    File file;

    FileReferenceInputStream(String path, InputStream is, File file) {
        this.path = path;
        this.is = UnicodeStream.wrap(is, StandardCharsets.UTF_8);
        this.file = file;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public FileReference withBytes() {
        try {
            return FileReference.fromBytes(path, IOUtils.toByteArray(is), file);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getSinglePassInputStream() {
        return is;
    }

    @Override
    public InputStream createInputStream() {
        throw new UnsupportedOperationException();
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
        return is.getEncoding();
    }
}
