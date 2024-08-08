package nl.inl.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.BOMInputStream;

public class FileReferenceBytes implements FileReference {

    String path;

    byte[] contents;

    File file;

    Charset charset;

    FileReferenceBytes(String path, byte[] contents, File file) {
        this.path = path;
        this.contents = contents;
        this.file = file;
    }

    FileReferenceBytes(String path, byte[] contents, Charset charset) {
        this.path = path;
        this.contents = contents;
        this.charset = charset;
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

    @Override
    public Charset getCharSet() {
        if (charset == null) {
            // Check the contents for a BOM
            BOMInputStream str = new BOMInputStream(new ByteArrayInputStream(contents));
            try {
                String name = str.getBOMCharsetName();
                charset = name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
            } catch (IOException e) {
                charset = StandardCharsets.UTF_8;
            }
        }
        return charset;
    }
}
