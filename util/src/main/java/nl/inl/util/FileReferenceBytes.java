package nl.inl.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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

    @Override
    public Charset getCharSet() {
        // Check the contents for a BOM
        if (contents.length >= 3 && contents[0] == (byte) 0xEF && contents[1] == (byte) 0xBB && contents[2] == (byte) 0xBF) {
            return Charset.forName("UTF-8");
        }
        if (contents.length >= 2 && contents[0] == (byte) 0xFF && contents[1] == (byte) 0xFE) {
            return Charset.forName("UTF-16LE");
        }
        if (contents.length >= 2 && contents[0] == (byte) 0xFE && contents[1] == (byte) 0xFF) {
            return Charset.forName("UTF-16BE");
        }
        if (contents.length >= 4 && contents[0] == (byte) 0x00 && contents[1] == (byte) 0x00 && contents[2] == (byte) 0xFE && contents[3] == (byte) 0xFF) {
            return Charset.forName("UTF-32BE");
        }
        if (contents.length >= 4 && contents[0] == (byte) 0xFF && contents[1] == (byte) 0xFE && contents[2] == (byte) 0x00 && contents[3] == (byte) 0x00) {
            return Charset.forName("UTF-32LE");
        }
        return StandardCharsets.UTF_8;
    }
}
