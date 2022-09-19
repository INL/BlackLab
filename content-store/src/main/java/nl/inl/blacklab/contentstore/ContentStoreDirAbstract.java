package nl.inl.blacklab.contentstore;

import java.io.File;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.util.VersionFile;

/**
 * Store string content by id in a directory of compound files with a TOC file.
 * Quickly retrieve (parts of) the string content.
 */
public abstract class ContentStoreDirAbstract extends ContentStoreExternal {
    /**
     * Dir to store the content and TOC
     */
    protected final File dir;
    
    protected ContentStoreDirAbstract(File dir) {
        this.dir = dir;
    }

    protected void setStoreType(String type, String version) {
        VersionFile.write(dir, type, version);
    }

    /**
     * Get the type and version of the content store
     * 
     * @param dir directory of the content store
     * @return the contents of the store's version file
     */
    public static VersionFile getStoreTypeVersion(File dir) throws ErrorOpeningIndex {
        VersionFile vf = new VersionFile(dir);
        if (vf.exists())
            vf.read();
        else
            throw new ErrorOpeningIndex("Content store directory must contain version file! (" + dir + ")");
        return vf;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + dir + ")";
    }

}
