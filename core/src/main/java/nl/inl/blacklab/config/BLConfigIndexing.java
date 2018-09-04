package nl.inl.blacklab.config;

public class BLConfigIndexing {
    boolean downloadAllowed = false;
    
    String downloadCacheDir = null;
    
    int downloadCacheSizeMegs = 100;
    
    int downloadCacheMaxFileSizeMegs = 100;
    
    int zipFilesMaxOpen = 10;

    public boolean isDownloadAllowed() {
        return downloadAllowed;
    }

    public void setDownloadAllowed(boolean downloadAllowed) {
        this.downloadAllowed = downloadAllowed;
    }

    public String getDownloadCacheDir() {
        return downloadCacheDir;
    }

    public void setDownloadCacheDir(String downloadCacheDir) {
        this.downloadCacheDir = downloadCacheDir;
    }

    public int getDownloadCacheSizeMegs() {
        return downloadCacheSizeMegs;
    }

    public void setDownloadCacheSizeMegs(int downloadCacheSizeMegs) {
        this.downloadCacheSizeMegs = downloadCacheSizeMegs;
    }

    public int getDownloadCacheMaxFileSizeMegs() {
        return downloadCacheMaxFileSizeMegs;
    }

    public void setDownloadCacheMaxFileSizeMegs(int downloadCacheMaxFileSizeMegs) {
        this.downloadCacheMaxFileSizeMegs = downloadCacheMaxFileSizeMegs;
    }

    public int getZipFilesMaxOpen() {
        return zipFilesMaxOpen;
    }

    public void setZipFilesMaxOpen(int zipFilesMaxOpen) {
        this.zipFilesMaxOpen = zipFilesMaxOpen;
    }
}