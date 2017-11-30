package nl.inl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Process (trees of) files, which may include archives
 * that we want to recursively process as well.
 */
public class FileProcessor implements AutoCloseable, TarGzipReader.FileHandler {

    /**
     * A way to handle files, including files inside archives.
     */
    public static interface FileHandler {

    	/**
    	 * @deprecated {@link #stream(String, InputStream)} will always be called in stead
    	 *
    	 * @param path
    	 * @param f
    	 */
    	@Deprecated
        void file(String path, File f);

    	/**
    	 * Handle a file stream.
    	 * This function may be called in multiple threads when {@link FileProcessor#useThreads} is true.
    	 *
    	 * NOTE: the InputStream should be closed by the handler.
    	 *
    	 * @param path
    	 * @param f
    	 */
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
     * Is a simple wrapper to call our filehandler from a different thread for a single file.
     * Should only be used with files that can be processed directly, not with archives/compressed files etc.
     */
    private final class CallFileHandlerTask implements Runnable {
    	private final String fileName;
    	private final InputStream is;

    	CallFileHandlerTask(String fileName, InputStream is) {
    		this.fileName = fileName;
    		this.is = is;
    	}

    	@Override
    	public void run() {
            try {
            	// TODO this is probably an issue, the stream might be within an archive
            	// this will then be called WHILE that same archive is being processed in another thread.
            	// how do we handle this? only handle actual File objects with threads maybe (like it seems it used to be?)
    			fileHandler.stream(fileName, is);
    		} catch (Exception e) {
    			System.err.println("Error while processing file: " + fileName);
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
	                System.err.println("Uncaught exception during file processing:");
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
        // TODO: is the old executor ever shut down if this is called after construction?
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
     * Process a file or directory.
     *
     * If this file is a directory, all child files will be processed, files within subdirectories will only be processed if {@link #isRecurseSubdirs()} is true.
     * For rules on how files are processed, regarding archives etc, see {@link #processInputStream(String, InputStream)}.
     *
     * @param fileOrDirToProcess file, directory or archive to process
     *
     * @throws FileNotFoundException
     */
    public void processFile(File fileOrDirToProcess) throws FileNotFoundException {
    	if (!fileOrDirToProcess.exists())
    		throw new FileNotFoundException("Input file or dir not found: " + fileOrDirToProcess);

    	if (fileOrDirToProcess.isDirectory()) {
    		 for (File childFile : FileUtil.listFilesSorted(fileOrDirToProcess)) {
    			if (!childFile.isDirectory() || recurseSubdirs) // Even if recurseSubdirs is false, we should process the parent dir's file contents
    				processFile(childFile);

	            if (!keepProcessing)
	                break;
	        }
        } else {
    		processInputStream(fileOrDirToProcess.getName(), new FileInputStream(fileOrDirToProcess));
        }
    }

    /**
     * Process from an InputStream, which may be an archive or a regular file.
     *
     * Archives (.zip and .tar.gz) will only be processed if {@link #isProcessArchives()} is true. GZipped files (.gz) will be unpacked regardless.
     * Note that in the case of archives, it will be treated as a flat file, meaning that all files are processed recursively, even within subdirectories.
     *
     * @param name
     *            name for the InputStream (e.g. name of the file)
     * @param is
     *            the stream
     */
    public void processInputStream(String name, InputStream is) {
        try {
        	if (isProcessArchives() && name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
        		TarGzipReader.processTarGzip(name, is, this);
        	} else if (isProcessArchives() && name.endsWith(".zip")) {
        		TarGzipReader.ProcessZip(name, is, this);
            } else if (name.endsWith(".gz")) {
                TarGzipReader.processGzip(name, is, this);
            } else if (!skipFile(name) && getFileNamePattern().matcher(name).matches()) {
            	if (useThreads) {
            		executor.execute(new CallFileHandlerTask(name, is));
            	} else {
            		fileHandler.stream(name,  is);
            	}
        	}
        } catch (Exception e) {
            keepProcessing = errorHandler.errorOccurred(name, null, e);
        }
    }

    // Callback from TarGzipReader to handle files inside archives
    @Override
    public boolean handle(String filePath, InputStream contents) {
    	processInputStream(filePath, contents);
    	return keepProcessing;
    }
}
