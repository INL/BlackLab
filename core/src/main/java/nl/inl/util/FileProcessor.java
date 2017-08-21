package nl.inl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Process (trees of) files, which may include archives
 * that we want to recursively process as well.
 */
public class FileProcessor implements AutoCloseable {

    /**
     * A way to handle files, including files inside archives.
     */
    public static interface FileHandler {

        void file(String path, File f);

        void stream(String path, InputStream f);
    }

    /**
     * Handles error, and decides whether to continue processing or not.
     */
    public static interface ErrorHandler {
        /**
         * Report an error and decide whether to continue or not.
         *
         * @param file file the error occurred in
         * @param msg error message (if any)
         * @param e exception (if any)
         * @return true if we should continue, false to abort
         */
        boolean errorOccurred(String file, String msg, Exception e);
    }

    /**
     * Simple error handler that reports errors and can abort or continue.
     */
    public static class SimpleErrorHandler implements ErrorHandler {

        private boolean continueOnError;

        public SimpleErrorHandler(boolean continueOnError) {
            this.continueOnError = continueOnError;
        }

        @Override
        public boolean errorOccurred(String file, String msg, Exception e) {
            if (msg != null) {
                System.err.println("ERROR while processing file " + file);
            }
            if (msg != null) {
                System.err.println("  " + msg);
            }
            if (e != null) {
                e.printStackTrace(System.err);
            }
            return continueOnError;
        }
    }

    /**
     * A task to process a file.
     *
     * Used for multi-threaded file processing.
     */
    private final class ProcessFileTask implements Runnable {
    	private final File fileToIndex;
    
    	ProcessFileTask(File fileToIndex) {
    		this.fileToIndex = fileToIndex;
    	}
    
    	@Override
    	public void run() {
            try {
    			String fn = fileToIndex.getCanonicalPath(); //Name();
    			if (processArchives && fn.endsWith(".zip")) {
    			    indexZip(fileToIndex);
    			} else {
    			    if (!skipFile(fileToIndex.getName())) {
    			        if (processArchives && fn.endsWith(".gz") || fn.endsWith(".tgz") || fn.endsWith(".zip")) {
    			            // Archive.
    			            try {
    			                try (FileInputStream is = new FileInputStream(fileToIndex)) {
    			                    processInputStream(fn, is);
    			                }
    			            } catch (Exception e) {
    			                keepProcessing = errorHandler.errorOccurred(fileToIndex.getPath(), null, e);
    			            }
    			        } else {
    			            // Regular file.
    			            fileHandler.file(fn, fileToIndex);
    			        }
    			    }
    			}
    		} catch (Exception e) {
    			System.err.println("Error while processing file: " + fileToIndex);
    			e.printStackTrace();
    			System.err.flush();
    		}
    	}
    }

    /** Catches any exceptions the Runnable throws so we can handle them. */
	private static class ExceptionCatchingThreadFactory implements ThreadFactory {
	    private final ThreadFactory delegate;

	    ExceptionCatchingThreadFactory(ThreadFactory delegate) {
	        this.delegate = delegate;
	    }

	    @Override
		public Thread newThread(final Runnable r) {
	        Thread t = delegate.newThread(r);
	        t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
	            @Override
	            public void uncaughtException(Thread t, Throwable e) {
	                System.err.println("Uncaught exception during indexing:");
	            	e.printStackTrace();
	            }
	        });
	        return t;
	    }
	}

    /** Restrict the files we handle to a file glob? */
    private Pattern pattGlob;

    /** Process subdirectories? */
    private boolean recurseSubdirs;

    /** Process archives as directories?
     *  Note that this setting is independent of recurseSubdirs; if this is true,
     *  files inside archives will be processed, even if recurseSubdirs is false.
     */
    boolean processArchives;

    /** Skip files like Thumbs.db (Windows) and .DS_Store (OSX)? */
    private boolean skipOsSpecialFiles = true;

    /** What to do with each file */
    FileHandler fileHandler;

    /** Decides whether or not to continue when an error occurs */
    ErrorHandler errorHandler = new SimpleErrorHandler(false);

    /** If false, we shouldn't process any more files */
    boolean keepProcessing;

    /** Process files in separate threads? */
    boolean useThreads = false;

    /** Executor used for processing files */
	private ThreadPoolExecutor executor;

    public FileProcessor(boolean useThreads) {
        this(useThreads, true, true);
    }

    public FileProcessor(boolean useThreads, boolean recurseSubdirs, boolean processArchives) {
    	this.useThreads = useThreads;
        this.recurseSubdirs = recurseSubdirs;
        this.processArchives = processArchives;
        setFileNameGlob("*");
        reset();
    }

    public void reset() {
        keepProcessing = true;
       	executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(useThreads ? 8 : 1);
		executor.setThreadFactory(new ExceptionCatchingThreadFactory(executor.getThreadFactory()));
    }

    public void setFileNameGlob(String glob) {
        pattGlob = Pattern.compile(FileUtil.globToRegex(glob));
    }

    public void setFileNamePattern(Pattern pattGlob) {
        this.pattGlob = pattGlob;
    }

    public Pattern getFileNamePattern() {
        return pattGlob;
    }

    public boolean isRecurseSubdirs() {
        return recurseSubdirs;
    }

    public void setRecurseSubdirs(boolean recurseSubdirs) {
        this.recurseSubdirs = recurseSubdirs;
    }

    public boolean isProcessArchives() {
        return processArchives;
    }

    public void setProcessArchives(boolean processArchives) {
        this.processArchives = processArchives;
    }

    public boolean isSkipOsSpecialFiles() {
        return skipOsSpecialFiles;
    }

    public void setSkipOsSpecialFiles(boolean skipOsSpecialFiles) {
        this.skipOsSpecialFiles = skipOsSpecialFiles;
    }

    /**
     * Should we skip the specified file because it is a special OS file?
     *
     * Skips Windows Thumbs.db file and Mac OSX .DS_Store file.
     *
     * @param fileName name of the file
     * @return true if we should skip it, false otherwise
     */
    protected boolean skipFile(String fileName) {
        return skipOsSpecialFiles && (fileName.equals("Thumbs.db") || fileName.equals(".DS_Store"));
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void setFileHandler(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    /**
     * Index a file, directory or archive.
     *
     * @param fileToIndex file, directory or archive to index
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     * @throws Exception
     * @throws IOException
     */
    public void processFile(File fileToIndex)
            throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
        if (fileToIndex.isDirectory()) {
            processDir(fileToIndex);
        } else {
    		Runnable runnable = new ProcessFileTask(fileToIndex);
    		executor.execute(runnable);
        }
    }

    /**
     * After adding the last processing task, call this to wait for all threads to finish processing.
     */
    @Override
	public void close() {
		try {
			executor.shutdown();
			// Wait for all threads to finish (Long.MAX_VALUE == "forever")
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Error while waiting for processing threads to finish", e);
		}
    }

    /**
     * Index a directory
     *
     * @param dir
     *            directory to index
     * @param recurseSubdirs
     *            recursively process subdirectories?
     * @throws UnsupportedEncodingException
     * @throws FileNotFoundException
     * @throws Exception
     * @throws IOException
     */
    private void processDir(File dir) throws UnsupportedEncodingException,
            FileNotFoundException, IOException, Exception {
        if (!dir.exists())
            throw new FileNotFoundException("Input dir not found: " + dir);
        if (!dir.isDirectory())
            throw new IOException("Specified input dir is not a directory: " + dir);
        for (File fileToIndex : FileUtil.listFilesSorted(dir)) {
            processFile(fileToIndex);
            if (!keepProcessing)
                break;
        }
    }

    /**
     * Index from an InputStream, which may be an archive.
     *
     * @param name
     *            name for the InputStream (e.g. name of the file)
     * @param is
     *            the stream
     */
    public void processInputStream(String name, InputStream is) {
        try {
            if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
                indexTarGzip(name, is);
            } else if (name.endsWith(".gz")) {
                indexGzip(name, is);
            } else if (name.endsWith(".zip")) {
                indexZipInputStream(name, is);
            } else {
                fileHandler.stream(name, is);
            }
        } catch (Exception e) {
            keepProcessing = errorHandler.errorOccurred(name, null, e);
        }
    }

    /**
     * Index files inside a zip file.
     *
     * Note that directory structure inside the zip file is ignored; files are indexed as if they
     * are one large directory.
     *
     * Also note that for accessing large ZIP files, you need Java 7 which supports the
     * ZIP64 format, otherwise you'll get the "invalid CEN header (bad signature)" error.
     *
     * @param zipFile
     *            the zip file
     * @param recurseArchives whether to process archives inside archives
     * @throws Exception
     */
    private void indexZipInputStream(String path, InputStream zipInputStream) throws Exception {
        try (ZipInputStream z = new ZipInputStream(zipInputStream)) {
            while (true) {
                ZipEntry e = z.getNextEntry();
                if (e == null)
                    break;
                if (e.isDirectory())
                    continue;

                String fileName = e.getName();
                boolean isArchive = fileName.endsWith(".zip") || fileName.endsWith(".gz") || fileName.endsWith(".tgz");
                boolean skipFile = skipFile(fileName);
                Matcher m = getFileNamePattern().matcher(fileName);
                if (!skipFile && (m.matches() || isArchive)) {
                    try {
                        if (!isArchive || processArchives)
                            processInputStream(fileName, z);
                    } catch (Exception ex) {
                        keepProcessing = errorHandler.errorOccurred(path, null, ex);
                    }
                }
                if (!keepProcessing)
                    break;
            }
        } catch (Exception e) {
            keepProcessing = errorHandler.errorOccurred(path, "Error opening zip file", e);
        }
    }

    /**
     * Index files inside a zip file.
     *
     * Note that directory structure inside the zip file is ignored; files are indexed as if they
     * are one large directory.
     *
     * Also note that for accessing large ZIP files, you need Java 7 which supports the
     * ZIP64 format, otherwise you'll get the "invalid CEN header (bad signature)" error.
     *
     * @param zipFile the zip file
     * @param recurseArchives whether to process archives inside archives
     * @throws Exception
     */
    void indexZip(File zipFile) throws Exception {
        if (!zipFile.exists())
            throw new FileNotFoundException("ZIP file not found: " + zipFile);
        try (ZipFile z = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> es = z.entries();
            while (es.hasMoreElements()) {
                ZipEntry e = es.nextElement();
                if (e.isDirectory())
                    continue;
                String fileName = e.getName();
                boolean isArchive = fileName.endsWith(".zip") || fileName.endsWith(".gz") || fileName.endsWith(".tgz");
                boolean skipFile = skipFile(fileName);
                Matcher m = getFileNamePattern().matcher(fileName);
                if (!skipFile && (m.matches() || isArchive)) {
                    String completePath = zipFile.getAbsolutePath() + "/" + fileName;
                    try {
                        try (InputStream is = z.getInputStream(e)) {
                            if (!isArchive || processArchives) {
                                processInputStream(completePath, is);
                            }
                        }
                    } catch (Exception ex) {
                        keepProcessing = errorHandler.errorOccurred(completePath, null, ex);
                    }
                }
                if (!keepProcessing)
                    break;
            }
        } catch (Exception e) {
            keepProcessing = errorHandler.errorOccurred(zipFile.getPath(), "Error opening zip file", e);
        }
    }

    private void indexGzip(final String gzFileName, InputStream gzipStream) {
        TarGzipReader.processGzip(gzFileName, gzipStream, new TarGzipReader.FileHandler() {
            @Override
            public boolean handle(String filePath, InputStream contents) {
                int i = filePath.lastIndexOf("/");
                String fileName = i < 0 ? filePath : filePath.substring(i + 1);
                if (!skipFile(fileName)) {
                    String completePath = gzFileName + "/" + filePath;
                    try {
                        processInputStream(completePath, contents);
                    } catch (Exception e) {
                        keepProcessing = getErrorHandler().errorOccurred(completePath, null, e);
                    }
                }
                return keepProcessing;
            }
        });
    }

    private void indexTarGzip(final String tgzFileName, InputStream tarGzipStream) {
        TarGzipReader.processTarGzip(tarGzipStream, new TarGzipReader.FileHandler() {
            @Override
            public boolean handle(String filePath, InputStream contents) {
                String completePath = tgzFileName + "/" + filePath;
                int i = filePath.lastIndexOf("/");
                String fileName = i < 0 ? filePath : filePath.substring(i + 1);
                if (!skipFile(fileName)) {
                    try {
                        File f = new File(filePath);
                        String fn = f.getName();
                        Matcher m = getFileNamePattern().matcher(fn);
                        if (m.matches()) {
                            processInputStream(completePath, contents);
                        } else {
                            boolean isArchive = fn.endsWith(".zip") || fn.endsWith(".gz") || fn.endsWith(".tgz");
                            if (isArchive && isProcessArchives()) {
                                processInputStream(completePath, contents);
                            }
                        }
                    } catch (Exception e) {
                        keepProcessing = getErrorHandler().errorOccurred(completePath, null, e);
                    }
                }
                return keepProcessing;
            }
        });
    }

}
