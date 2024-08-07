package nl.inl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

public class FileReferenceFile implements FileReference {

    File file;

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
        return FileReference.fromBytes(getPath(), getBytes(), file);
    }

    @Override
    public FileReference withCreateInputStream() {
        return this;
    }

    @Override
    public byte[] getBytes() {
        try {
            return FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
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
}
