package nl.inl.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

public class FileReferenceBytes implements FileReference {

    String path;

    byte[] contents;

    File file;

    FileReferenceBytes(String path, byte[] contents, File file) {
        this.path = path;
        this.contents = contents;
        this.file = file;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public FileReference withBytes() {
        return this;
    }

    @Override
    public FileReference withCreateInputStream() {
        return this;
    }

    @Override
    public byte[] getBytes() {
        return contents;
    }

    @Override
    public InputStream getSinglePassInputStream() {
        return createInputStream();
    }

    @Override
    public InputStream createInputStream() {
        return new ByteArrayInputStream(contents);
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public File getAssociatedFile() {
        return file;
    }
}
