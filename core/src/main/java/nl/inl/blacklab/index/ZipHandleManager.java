package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * Manages opened zip files.
 *
 * Openings large zip files takes time, so it's more efficient to keep zip files
 * open for a while in case we'll access the same zip file again. Of course, we
 * should eventually close them to free up resources as well.
 */
public class ZipHandleManager {

    /** Do we want to keep zip files open at all? */
    private static boolean keepZipsOpen = true;

    /** Maximum age of open zip file in sec */
    private static int maxOpenZipAgeSec = 24 * 3600;

    /** Maximum number of zip files to keep open */
    private static int maxOpenZipFiles = 10;

    /** Zip files opened by DocIndexerBase indexers. Should be closed eventually. */
    private static Map<File, ZipHandle> openZips = new LinkedHashMap<>();

    static class ZipHandle implements Comparable<ZipHandle> {

        public File key;

        public ZipFile zipFile;

        public long lastUsed;

        public ZipHandle(File key, ZipFile file) {
            this.key = key;
            this.zipFile = file;
            this.lastUsed = System.currentTimeMillis();
        }

        @Override
        public int compareTo(ZipHandle o) {
            return Long.compare(lastUsed, o.lastUsed);
        }

        public void markUsed() {
            lastUsed = System.currentTimeMillis();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((key == null) ? 0 : key.hashCode());
            result = prime * result + (int) (lastUsed ^ (lastUsed >>> 32));
            result = prime * result + ((zipFile == null) ? 0 : zipFile.hashCode());
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
            ZipHandle other = (ZipHandle) obj;
            if (key == null) {
                if (other.key != null)
                    return false;
            } else if (!key.equals(other.key))
                return false;
            if (lastUsed != other.lastUsed)
                return false;
            if (zipFile == null) {
                if (other.zipFile != null)
                    return false;
            } else if (!zipFile.equals(other.zipFile))
                return false;
            return true;
        }

        public long timeSinceLastUsed() {
            return System.currentTimeMillis() - lastUsed;
        }

        public void close() {
            try {
                zipFile.close();
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }

    }

    public static void closeAllZips() {
        synchronized (openZips) {
            // We don't close linked document zips immediately; closing them when you're likely to
            // reuse them soon is inefficient.
            // (we should probably keep track of last access and close them eventually, though)
            Iterator<ZipHandle> it = openZips.values().iterator();
            while (it.hasNext()) {
                it.next().close(); // close zip file
                it.remove();
            }
        }
    }

    private synchronized static void removeEntriesIfRequired() {

        // Remove any entries that haven't been used for a long time
        List<ZipHandle> zl = new ArrayList<>(openZips.values());
        Iterator<ZipHandle> it = zl.iterator();
        while (it.hasNext()) {
            ZipHandle zh = it.next();
            if (zh.timeSinceLastUsed() > maxOpenZipAgeSec) {
                openZips.remove(zh.key);
                zh.close();
                it.remove();
            }
        }

        // If too many zips are open, close ones that haven't been used the longest
        if (openZips.size() > maxOpenZipFiles) {
            zl.sort(Comparator.naturalOrder());
            it = openZips.values().iterator();
            while (openZips.size() > maxOpenZipFiles && it.hasNext()) {
                ZipHandle zh = it.next();
                zh.close(); // delete the file
                it.remove();
            }
        }
    }

    public static ZipFile openZip(File zipFile) throws IOException {
        if (!keepZipsOpen)
            return new ZipFile(zipFile);
        synchronized (openZips) {
            ZipHandle z = openZips.get(zipFile);
            if (z == null) {
                z = new ZipHandle(zipFile, new ZipFile(zipFile));
                openZips.put(zipFile, z);
            }
            z.markUsed();
            removeEntriesIfRequired();
            return z.zipFile;
        }
    }

    public static void setKeepZipsOpen(boolean keepZipsOpen) {
        ZipHandleManager.keepZipsOpen = keepZipsOpen;
    }

    public static void setMaxOpenZipAgeSec(int maxOpenZipAgeSec) {
        ZipHandleManager.maxOpenZipAgeSec = maxOpenZipAgeSec;
    }

    public static void setMaxOpen(int maxOpenZipFiles) {
        ZipHandleManager.maxOpenZipFiles = maxOpenZipFiles;
    }

}
