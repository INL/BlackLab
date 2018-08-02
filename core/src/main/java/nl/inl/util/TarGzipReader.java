package nl.inl.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FilenameUtils;

/**
 * Class for handling .tar.gz input streams. It will call the supplied handler
 * for each "normal" file in the .tar.gz file.
 */
public class TarGzipReader {

    /**
     * Handles a file inside the .tar.gz archive.
     */
    @FunctionalInterface
    public interface FileHandler {
        /**
         * Handle a file inside the .zip or .tar.gz archive.
         *
         * @param filePath path to the archive, concatenated with any path to this file
         *            inside the archive
         * @param contents file contents, this stream is separate from the stream being
         *            processed, and will always remain open until it is closed by the
         *            handler.
         * @return true if we should continue with the next file, false if not
         */
        boolean handle(String filePath, InputStream contents);
    }

    /**
     * Process a .tar.gz file and call the handler for each normal file in the
     * archive.
     *
     * @param fileName name/path to this file
     * @param tarGzipStream the .tar.gz input stream to decompress. The stream will
     *            be closed after processing.
     * @param fileHandler the handler to call for each regular file
     */
    public static void processTarGzip(String fileName, InputStream tarGzipStream, FileHandler fileHandler) {
        try (InputStream unzipped = new GzipCompressorInputStream(tarGzipStream)) {
            processTar(fileName, unzipped, fileHandler);
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }
    }

    /**
     * Process a .gz file and call the file handler for its contents.
     *
     * @param fileName name/path to this file
     * @param gzipStream the .gz input stream to decompress. The stream will be
     *            closed after processing.
     * @param fileHandler the handler to call
     */
    public static void processGzip(String fileName, InputStream gzipStream, FileHandler fileHandler) {
        try (InputStream unzipped = new GzipCompressorInputStream(gzipStream)) {
            // Make a copy of the data, since the stream we pass into fileHandler.handle may be used within another thread
            // so it (or any of its underlying streams) should not be closed by us inadvertantly
            InputStream callbackStream = new ByteArrayInputStream(org.apache.commons.io.IOUtils.toByteArray(unzipped));
            fileHandler.handle(fileName.replaceAll("\\.gz$", ""), callbackStream); // TODO make filename handling uniform across all archives types?
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }
    }

    /**
     * Process a .tar file and call the handler for each normal file in the archive.
     *
     * @param fileName name/path to this file
     * @param tarStream the .tar input stream to decompress. The stream will be
     *            closed after processing.
     * @param fileHandler the handler to call for each regular file
     */
    public static void processTar(String fileName, InputStream tarStream, FileHandler fileHandler) {
        try (TarArchiveInputStream s = new TarArchiveInputStream(tarStream)) {
            for (TarArchiveEntry e = s.getNextTarEntry(); e != null; e = s.getNextTarEntry()) {
                if (e.isDirectory())
                    continue;

                // Make a copy of this file's data, since there is a real chance the returned stream will be processed by another thread.
                // It makes sense too, the stream has a bunch of internal data about which entry is currently being processed
                // and if we jump to the next entry in between reads in another thread things would go badly.
                // NOTE: InputStream is not closed, handler is responsible for closing its stream
                ByteArrayInputStream decoded = new ByteArrayInputStream(IOUtils.toByteArray(s));
                boolean keepProcessing = fileHandler.handle(FilenameUtils.concat(fileName, e.getName()), decoded);
                if (!keepProcessing)
                    return;
            }
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }
    }

    /**
     * Process a .zip file and call the handler for each normal file in the archive.
     *
     * Note that directory structure inside the zip file is ignored; files are
     * processed as if they are one large directory.
     *
     * @param fileName name/path to this file
     * @param zipStream the .zip input stream to decompress. The stream will be
     *            closed after processing.
     * @param fileHandler the handler to call for each regular file
     */
    public static void processZip(String fileName, InputStream zipStream, FileHandler fileHandler) {
        try (ZipInputStream s = new ZipInputStream(zipStream)) {
            for (ZipEntry e = s.getNextEntry(); e != null; e = s.getNextEntry()) {
                if (e.isDirectory())
                    continue;

                // Make a copy of this file's data, since there is a real chance the returned stream will be processed by another thread.
                // It makes sense too, the stream has a bunch of internal data about which entry is currently being processed
                // and if we jump to the next entry in between reads in another thread things would go badly.
                // NOTE: InputStream is not closed, handler is responsible for closing its stream
                ByteArrayInputStream decoded = new ByteArrayInputStream(IOUtils.toByteArray(s));
                boolean keepProcessing = fileHandler.handle(FilenameUtils.concat(fileName, e.getName()), decoded);
                if (!keepProcessing)
                    return;
            }
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }
    }
}
