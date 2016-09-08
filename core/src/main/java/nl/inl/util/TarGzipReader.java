package nl.inl.util;

import java.io.FilterInputStream;
import java.io.InputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;


/**
 * Class for handling .tar.gz input streams. It will call the supplied handler for each "normal"
 * file in the .tar.gz file.
 */
public class TarGzipReader {

	/**
	 * Handles a file inside the .tar.gz archive.
	 */
	public interface FileHandler {
		/**
		 * Handles a file inside the .tar.gz archive.
		 * @param filePath path inside the archive
		 * @param contents file contents
		 * @return true if we should continue with the next file, false if not
		 */
		boolean handle(String filePath, InputStream contents);
	}

	/**
	 * Process a .tar.gz file and call the handler for each normal file in the archive.
	 * @param tarGzipStream the .tar.gz input stream to decompress
	 * @param fileHandler the handler to call for each regular file
	 */
	public static void processTarGzip(InputStream tarGzipStream, FileHandler fileHandler) {
		try {
			InputStream unzipped = new GzipCompressorInputStream(tarGzipStream);
			processTar(unzipped, fileHandler);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Process a .gz file and call the file handler for its contents.
	 * @param filePath
	 * @param gzipStream the .gz input stream to decompress
	 * @param fileHandler the handler to call
	 */
	public static void processGzip(String filePath, InputStream gzipStream, FileHandler fileHandler) {
		try {
			InputStream unzipped = new GzipCompressorInputStream(gzipStream);
			fileHandler.handle(filePath.replaceAll("\\.gz$", ""), unzipped);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Process a .tar file and call the handler for each normal file in the archive.
	 * @param tarStream the .tar input stream to decompress
	 * @param fileHandler the handler to call for each regular file
	 */
	public static void processTar(InputStream tarStream, FileHandler fileHandler) {
		try {
			try (TarArchiveInputStream untarred = new TarArchiveInputStream(tarStream)) {
				InputStream uncloseableInputStream = new FilterInputStream(untarred) {
					@Override
					public void close() {
						// Don't close!
						// (when Reader is GC'ed, closes stream prematurely..?)
					}
				};
				boolean continueReading = true;
				while(continueReading) {
					// Go to the next file in the .tar
					TarArchiveEntry tarEntry = untarred.getNextTarEntry();
					if (tarEntry == null)
						break;
					if (!tarEntry.isFile()) {
						continue;
					}

					String filePath = tarEntry.getName();
					continueReading = fileHandler.handle(filePath, uncloseableInputStream);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
