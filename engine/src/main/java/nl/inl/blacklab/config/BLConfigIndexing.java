package nl.inl.blacklab.config;

import nl.inl.util.DownloadCache;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldImpl;

public class BLConfigIndexing {

    /** Max tokens for a private user index, default 100M **/
    private long userIndexMaxTokenCount = 100_000_000;

    boolean downloadAllowed = false;
    
    String downloadCacheDir = null;
    
    int downloadCacheSizeMegs = 100;
    
    int downloadCacheMaxFileSizeMegs = 100;
    
    int zipFilesMaxOpen = 10;
    
    int maxMetadataValuesToStore = MetadataFieldImpl.maxMetadataValuesToStore();
    
    int numberOfThreads = 2;

    int maxNumberOfIndicesPerUser = 10;

    public DownloadCache.Config downloadCacheConfig() {
        return new DownloadCache.Config() {
            @Override
            public boolean isDownloadAllowed() {
                return BLConfigIndexing.this.isDownloadAllowed();
            }

            @Override
            public String getDir() {
                return getDownloadCacheDir();
            }

            @Override
            public long getSize() {
                return getDownloadCacheSizeMegs() * 1_000_000L;
            }

            @Override
            public long getMaxFileSize() {
                return getDownloadCacheMaxFileSizeMegs() * 1_000_000L;
            }
        };
    }

    public boolean isDownloadAllowed() {
        return downloadAllowed;
    }

    @SuppressWarnings("unused")
    public void setDownloadAllowed(boolean downloadAllowed) {
        this.downloadAllowed = downloadAllowed;
    }

    public String getDownloadCacheDir() {
        return downloadCacheDir;
    }

    @SuppressWarnings("unused")
    public void setDownloadCacheDir(String downloadCacheDir) {
        this.downloadCacheDir = downloadCacheDir;
    }

    public int getDownloadCacheSizeMegs() {
        return downloadCacheSizeMegs;
    }

    @SuppressWarnings("unused")
    public void setDownloadCacheSizeMegs(int downloadCacheSizeMegs) {
        this.downloadCacheSizeMegs = downloadCacheSizeMegs;
    }

    public int getDownloadCacheMaxFileSizeMegs() {
        return downloadCacheMaxFileSizeMegs;
    }

    @SuppressWarnings("unused")
    public void setDownloadCacheMaxFileSizeMegs(int downloadCacheMaxFileSizeMegs) {
        this.downloadCacheMaxFileSizeMegs = downloadCacheMaxFileSizeMegs;
    }

    public int getZipFilesMaxOpen() {
        return zipFilesMaxOpen;
    }

    @SuppressWarnings("unused")
    public void setZipFilesMaxOpen(int zipFilesMaxOpen) {
        this.zipFilesMaxOpen = zipFilesMaxOpen;
    }

    public int getMaxMetadataValuesToStore() {
        return maxMetadataValuesToStore;
    }

    @SuppressWarnings("unused")
    public void setMaxMetadataValuesToStore(int maxMetadataValuesToStore) {
        this.maxMetadataValuesToStore = maxMetadataValuesToStore;
        MetadataFieldImpl.setMaxMetadataValuesToStore(maxMetadataValuesToStore);
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    @SuppressWarnings("unused")
    public void setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
    }

    public int getMaxNumberOfIndicesPerUser() {
        return maxNumberOfIndicesPerUser;
    }

    @SuppressWarnings("unused")
    public void setMaxNumberOfIndicesPerUser(int maxNumberOfIndicesPerUser) {
        this.maxNumberOfIndicesPerUser = maxNumberOfIndicesPerUser;
    }

    @SuppressWarnings("unused")
    public void setUserIndexMaxTokenCount(int maxTokenCount) {
        this.userIndexMaxTokenCount = maxTokenCount;
    }

    public long getUserIndexMaxTokenCount() {
        return this.userIndexMaxTokenCount;
    }
}
