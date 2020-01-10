package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Manages downloaded files.
 *
 * Downloading files takes time, so it's more efficient to keep them around for
 * a while in case we'll access the same file again. Of course, we should
 * eventually delete them to free up resources as well.
 */
public class DownloadCache {

    /**
     * Is DocIndexerBase allowed to download files? (defaults to false for security;
     * explicitly set it to true if you want this)
     */
    private static boolean fileDownloadAllowed = false;

    /** Maximum size of (linked, e.g. metadata) files downloaded */
    private static long maxDownloadedFileSize = 10_000_000;

    /** Maximum age of downloaded file in sec */
    private static int maxDownloadAgeSec = 24 * 3600;

    /** Maximum size of all files downloaded combined */
    private static int maxDownloadFolderSize = 100_000_000;

    /** Where to download files (or null to use the system temp dir) */
    private static File downloadTempDir;

    /** Files we've downloaded to a temp dir. Will be deleted on exit. */
    static Map<String, Download> downloadedFiles = new HashMap<>();

    static long downloadFolderSize = 0;

    static class Download implements Comparable<Download> {

        public String key;

        public File file;

        public long lastUsed;

        public Download(String key, File file) {
            this.key = key;
            this.file = file;
            this.lastUsed = System.currentTimeMillis();
        }

        @Override
        public int compareTo(Download o) {
            return Long.compare(lastUsed, o.lastUsed);
        }

        public void markUsed() {
            lastUsed = System.currentTimeMillis();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((file == null) ? 0 : file.hashCode());
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + (int) (lastUsed ^ (lastUsed >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Download other = (Download) obj;
            if (file == null) {
                if (other.file != null)
                    return false;
            } else if (!file.equals(other.file))
                return false;
            if (key == null) {
                if (other.key != null)
                    return false;
            } else if (!key.equals(other.key))
                return false;
            return lastUsed == other.lastUsed;
        }

        public long timeSinceLastUsed() {
            return System.currentTimeMillis() - lastUsed;
        }

        public void delete() {
            if (file.exists() && !file.delete())
                throw new BlackLabRuntimeException("Unable to delete downloaded file: " + file);
        }

        public long size() {
            return file.length();
        }

    }

    /**
     * Check the size of a file pointed to by a URL.
     *
     * @param url url to check
     * @return file size
     */
    private static int getUrlSize(URL url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("HEAD");
            c.getInputStream();
            return c.getContentLength();
        } catch (IOException e) {
            return -1;
        } finally {
            if (c != null)
                c.disconnect();
        }
    }

    private synchronized static void removeEntriesIfRequired() {

        // Remove any entries that haven't been used for a long time
        List<Download> dl = new ArrayList<>(downloadedFiles.values());
        Iterator<Download> it = dl.iterator();
        while (it.hasNext()) {
            Download download = it.next();
            if (download.timeSinceLastUsed() > maxDownloadAgeSec) {
                downloadedFiles.remove(download.key);
                downloadFolderSize -= download.size();
                download.delete();
                it.remove();
            }
        }

        // If the cache is too big, remove entries that haven't been used the longest
        if (downloadFolderSize > maxDownloadFolderSize) {
            dl.sort(Comparator.naturalOrder());
            it = downloadedFiles.values().iterator();
            while (downloadFolderSize > maxDownloadFolderSize && it.hasNext()) {
                Download download = it.next();
                downloadFolderSize -= download.size();
                download.delete(); // delete the file
                it.remove();
            }
        }
    }

    /**
     * Download file to temp file if it hasn't been downloaded already.
     *
     * @param inputFile URL of the file
     * @return temp file
     * @throws IOException
     * @throws MalformedURLException
     */
    public synchronized static File downloadFile(String inputFile) throws IOException, MalformedURLException {
        if (!isFileDownloadAllowed())
            throw new AccessDeniedException(inputFile, null, "Http downloads have been disabled");
        Download download = downloadedFiles.get(inputFile);
        if (download == null) {
            URL url = new URL(inputFile);
            int urlSize = getUrlSize(url);
            if (urlSize > maxDownloadedFileSize)
                throw new UnsupportedOperationException(
                        "File too large (" + urlSize + " > " + maxDownloadedFileSize + ")");
            String ext = inputFile.replaceAll("^.+(\\.[^\\.]+)$", "$1");
            if (ext == null || ext.isEmpty())
                ext = ".xml";
            File tempFile = File.createTempFile("BlackLab_download_", ext, getDownloadTempDir());
            tempFile.deleteOnExit();
            FileUtils.copyURLToFile(url, tempFile);
            download = new Download(inputFile, tempFile);
            downloadedFiles.put(inputFile, download);
            downloadFolderSize += tempFile.length();
        }
        download.markUsed();
        removeEntriesIfRequired();
        return download.file;
    }

    public static boolean isFileDownloadAllowed() {
        return fileDownloadAllowed;
    }

    public static void setDownloadAllowed(boolean fileDownloadAllowed) {
        DownloadCache.fileDownloadAllowed = fileDownloadAllowed;
    }

    public static void setMaxFileSizeMegs(int maxDownloadedFileSizeMegs) {
        maxDownloadedFileSize = maxDownloadedFileSizeMegs * 1_000_000L;
        if (maxDownloadFolderSize < maxDownloadedFileSize)
            maxDownloadedFileSize = maxDownloadFolderSize;
    }

    public synchronized static File getDownloadTempDir() {
        if (downloadTempDir == null) {
            downloadTempDir = new File(System.getProperty("java.io.tmpdir"), "bls-download-cache");
        }
        if (!downloadTempDir.exists()) {
            if (!downloadTempDir.mkdir())
                throw new BlackLabRuntimeException("Could not create dir: " + downloadTempDir);
            downloadTempDir.deleteOnExit();
        }
        return downloadTempDir;
    }

    public static void setDir(File downloadTempDir) {
        DownloadCache.downloadTempDir = downloadTempDir;
    }

    public static void setSizeMegs(int maxDownloadFolderSizeMegs) {
        maxDownloadFolderSize = maxDownloadFolderSizeMegs * 1_000_000;
        if (maxDownloadFolderSize < maxDownloadedFileSize)
            maxDownloadedFileSize = maxDownloadFolderSize;
    }

}
