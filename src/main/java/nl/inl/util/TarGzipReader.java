package nl.inl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


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

	/** Class for decompressing gzip stream */
	private static Class<?> classGzipCompressorInputStream;

	/** Class for reading tar archive */
	private static Class<?> classTarArchiveInputStream;

	/** Constructor for creating GzipCompressorInputStream instances */
	private static Constructor<InputStream> ctorGzip;

	/** Constructor for creating TarArchiveInputStream instances */
	private static Constructor<InputStream> ctorTar;

	/** Method for getting next file entry in a tar archive */
	private static Method methodGetNextTarEntry;

	/** Class representing entry in tar archive */
	private static Class<?> classTarArchiveEntry;

	/** Method for getting name of a tar entry */
	private static Method methodGetName;

	/** Method for checking if a tar entry is a normal file */
	private static Method methodIsFile;

	/** True iff Apache commons-compress libs were found on the classpath */
	private static boolean tarGzLibsAvailable;

	static {
		init();
	}

	/** Detect Apache commons-compress and initialize the class and method variables. */
	@SuppressWarnings("unchecked")
	private static void init() {
		try {
			classGzipCompressorInputStream = Class.forName("org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream");
			classTarArchiveInputStream = Class.forName("org.apache.commons.compress.archivers.tar.TarArchiveInputStream");
			methodGetNextTarEntry = classTarArchiveInputStream.getMethod("getNextTarEntry");
			classTarArchiveEntry = Class.forName("org.apache.commons.compress.archivers.tar.TarArchiveEntry");
			methodGetName = classTarArchiveEntry.getMethod("getName");
			methodIsFile = classTarArchiveEntry.getMethod("isFile");
			ctorGzip = (Constructor<InputStream>)classGzipCompressorInputStream.getConstructor(InputStream.class);
			ctorTar = (Constructor<InputStream>)classTarArchiveInputStream.getConstructor(InputStream.class);
			tarGzLibsAvailable = true;
		} catch (Exception e) {
			tarGzLibsAvailable = false;
		}
	}

	/**
	 * Checks if we can process .tar.gz files
	 * @return true iff we can process .tar.gz files
	 */
	public static boolean canProcessTarGzip() {
		return tarGzLibsAvailable;
	}

	/**
	 * Checks if we can process .tar files
	 * @return true iff we can process .tar.gz files
	 */
	public static boolean canProcessTar() {
		return tarGzLibsAvailable;
	}

	/**
	 * Process a .tar.gz file and call the handler for each normal file in the archive.
	 * @param tarGzipStream the .tar.gz input stream to decompress
	 * @param fileHandler the handler to call for each regular file
	 */
	public static void processTarGzip(InputStream tarGzipStream, FileHandler fileHandler) {
		if (!tarGzLibsAvailable)
			throw new UnsupportedOperationException("Cannot process .tar.gz file; Apache commons-compress not found on the classpath.");
		try {
			InputStream unzipped = ctorGzip.newInstance(tarGzipStream);
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
		if (!tarGzLibsAvailable)
			throw new UnsupportedOperationException("Cannot process .gz file; Apache commons-compress not found on the classpath.");
		try {
			InputStream unzipped = ctorGzip.newInstance(gzipStream);
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
		if (!tarGzLibsAvailable)
			throw new UnsupportedOperationException("Cannot process .tar file; Apache commons-compress not found on the classpath.");
		try {
			InputStream untarred = ctorTar.newInstance(tarStream);
			InputStream uncloseableInputStream = new FilterInputStream(untarred) {
				@Override
				public void close() {
					// Don't close!
					// (when Reader is GC'ed, closes stream prematurely..?)
				}
			};
			try {
				boolean continueReading = true;
				while(continueReading) {
					// Go to the next file in the .tar
					Object tarEntry = methodGetNextTarEntry.invoke(untarred);
					if (tarEntry == null)
						break;
					if (!((Boolean)methodIsFile.invoke(tarEntry))) {
						continue;
					}

					String filePath = (String)methodGetName.invoke(tarEntry);
					continueReading = fileHandler.handle(filePath, uncloseableInputStream);
				}
			} finally {
				untarred.close();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) throws IOException  {
		File dir = new File("G:\\Jan_OpenSonar\\FoLiA");
		File tarGzFile = new File(dir, "SoNaR500.Curated.WR-P-E-E_newsletters.20130312.tar.gz");
		FileInputStream fileInputStream = new FileInputStream(tarGzFile);
		try {
			TarGzipReader.processTarGzip(fileInputStream, new FileHandler() {
				@Override
				public boolean handle(String filePath, InputStream contents) {
					try {
						// Print filename
						System.out.println(filePath);

						// Read file contents
						BufferedReader r = IoUtil.makeBuffered(new InputStreamReader(contents, "utf-8"));
						int linesPrinted = 0;
						while (true) {
							String line = r.readLine();
							if (line == null)
								break;
							linesPrinted++;
							if (linesPrinted >= 10)
								break;
							System.out.println(line);
						}
						return true;
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			});
		} finally {
			fileInputStream.close();
		}
	}
}
