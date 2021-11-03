package nl.inl.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.index.ZipHandleManager;

/**
 * Process (trees of) files, which may include archives that we want to
 * recursively process as well. This class is thread-safe as long as no
 * configuration is changed during processing.
 */
public class FileProcessor implements AutoCloseable {
    public interface FileHandler {
        /**
         * Handle a directory.
         * <p>
         * Called for all processed child (and descendant if
         * {@link FileProcessor#isRecurseSubdirs()} directories of the input file,
         * excluding the input directory itself. NOTE: This is only called for regular
         * directories, and not for archives or processed directories within archives.
         * NOTE: {@link FileProcessor#pattGlob} is NOT applied to directories. So the
         * directory names may not match the provided pattern.
         * <p>
         * This function may be called in multiple threads when FileProcessor was
         * created with thread support (see
         * {@link FileProcessor#FileProcessor(int, boolean, boolean)})
         *
         * @param dir the directory
         * @throws Exception these will be passed to
         *             {@link ErrorHandler#errorOccurred(Throwable, String, File)}
         */
        void directory(File dir) throws Exception;

        /**
         * Handle a file stream.
         * <p>
         * For effiency, the {@link FileHandler#file(String, byte[], File)} version will almost always be used.
         * The sole exception is when the {@link FileProcessor#processInputStream(String, InputStream, File)} is called
         * with a file that is not an archive. Files within archives are always returned as byte[].
         * <p>
         * This function may be called in multiple threads when FileProcessor was
         * created with thread support (see
         * {@link FileProcessor#FileProcessor(int, boolean, boolean)}) <br>
         * NOTE: the InputStream should be closed by the implementation.
         *
         * @param path filename, including path inside archives (if the file is within
         *            an archive)
         * @param is
         * @param file (optional, if known) the file from which the InputStream was
         *            built, or - if the InputStream is a file within an archive - the
         *            archive.
         * @throws Exception these will be passed to
         *             {@link ErrorHandler#errorOccurred(Throwable, String, File)}
         */
        void file(String path, InputStream is, File file) throws Exception;

        /**
         * Handle a byte array.
         * <p>
         * Called for all processed files that match the {@link FileProcessor#pattGlob},
         * including the input file. Not called for archives if
         * {@link FileProcessor#isProcessArchives()} is true (though it will then be
         * called for files within those archives).
         * <p>
         * This function may be called in multiple threads when FileProcessor was
         * created with thread support (see
         * {@link FileProcessor#FileProcessor(int, boolean, boolean)}) <br>
         * NOTE: the InputStream should be closed by the implementation.
         *
         * @param path filename, including path inside archives (if the file is within
         *            an archive)
         * @param contents file contents
         * @param file (optional, if known) the file from which the InputStream was
         *            built, or - if the InputStream is a file within an archive - the
         *            archive.
         * @throws Exception these will be passed to
         *             {@link ErrorHandler#errorOccurred(Throwable, String, File)}
         */
        void file(String path, byte[] contents, File file) throws Exception;

        // Regular file(File f) function is omitted on purpose.
        // As we process regular files as well as "virtual" files (entries in archives and the like) in the same manner.
        // This means in some cases there is no actual file backing up the data
    }

    /**
     * Handles error, and decides whether to continue processing or not.
     */
    @FunctionalInterface
    public interface ErrorHandler {

        /**
         * Report an error and decide whether to continue or not.
         *
         * @param e the exception
         * @param path path to the file that the error occurred in. This includes
         *            pathing in archives if the file is inside an archive.
         * @param f (optional, if known) the file from which the InputStream was built,
         *            or - if the InputStream is a file within an archive - the archive.
         * @return true if we should continue, false to abort
         */
        boolean errorOccurred(Throwable e, String path, File f);
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
        public synchronized boolean errorOccurred(Throwable e, String path, File f) {
            System.err.println("Error processing file " + (f != null ? f.toString() : path));
            e.printStackTrace(System.err);
            return continueOnError;
        }
    }

    private interface PathCapturingFileHandler extends FileHandler {
        byte[] getFile();
    }

    public static byte[] fetchFileFromArchive(File f, final String pathInsideArchive) {
        if (f.getName().endsWith(".gz") || f.getName().endsWith(".tgz")) {
            // We have to process the whole file, we can't do random access.
            PathCapturingFileHandler fileCapturer = new PathCapturingFileHandler() {
                byte[] file;

                @Override
                public void directory(File dir) throws Exception {
                    //
                }

                @Override
                public void file(String path, InputStream is, File archive) throws Exception {
                    if (path.endsWith(pathInsideArchive))
                        this.file = IOUtils.toByteArray(is);
                }

                @Override
                public void file(String path, byte[] content, File archive) {
                    if (path.endsWith(pathInsideArchive))
                        this.file = content;
                }

                @Override
                public byte[] getFile() {
                    return file;
                }
            };
            try (FileProcessor proc = new FileProcessor(1, false, true)) {
                proc.setFileHandler(fileCapturer);
                proc.processFile(f);
            }

            // FileProcessor must have completed/be closed before result is available
            return fileCapturer.getFile();
        } else if (f.getName().endsWith(".zip")) {
            // We can do random access. Fetch the file we want.
            try {
                ZipFile z = ZipHandleManager.openZip(f);
                ZipEntry e = z.getEntry(pathInsideArchive);
                if (e == null) {
                    throw new BlackLabRuntimeException("Linked document " + pathInsideArchive + " not found in archive " + f);
                }
                try (InputStream is = z.getInputStream(e)) {
                    return IOUtils.toByteArray(is);
                }
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported archive type: " + f.getName());
        }
    }


    /**
     * Restrict the files we handle to a file glob? Note that this pattern is not
     * applied to directories, and directories within archives. It is also not
     * applied to the input file directly.
     */
    private Pattern pattGlob;

    /** Process sub directories? */
    private boolean recurseSubdirs;

    /**
     * Process archives as directories? Note that this setting is independent of
     * recurseSubdirs; if this is true, files inside archives will be processed,
     * even if recurseSubdirs is false.
     */
    private boolean processArchives;

    /** Skip files like Thumbs.db (Windows) and .DS_Store (OSX)? */
    private boolean skipOsSpecialFiles = true;

    /** What to do with each file */
    private FileHandler fileHandler;

    /** Decides whether or not to continue when an error occurs */
    private ErrorHandler errorHandler = new SimpleErrorHandler(false);

    /**
     * Executor used for processing files, uses {@link MainThreadExecutorService} if
     * FileProcess was constructor with useThreads = false
     */
    private ExecutorService executor = null;

    /**
     * FileProcessor operates in two distinct stages: - The traversal of
     * directories/archives, this is done on the "main" thread (i.e. the thread that
     * initially called processFile/processInputStream) - Handling of all
     * files/entries, this is usually done asynchronously by our Handler.
     *
     * If an exception occurs in the handling stage, we want to stop all ongoing and
     * queued handlers, but also stop the the main thread if it's still busy
     * traversing and creating more handlers. The problem is that the main can't
     * directly act on exceptions thrown in handlers, as the exception is thrown
     * asynchronously.
     *
     * So we need a way to signal the main thread to cease all work: - aborting all
     * handlers/tasks is easy, we can shut down the ExcecutorService directly from
     * the handler thread when the exception occurs. - aborting the main thread will
     * require setting some flag and some manual checking on its part we could call
     * Thread.interrupt() on the main thread, but this would require the handlers to
     * keep a reference to the main thread so instead just use this flag that the
     * main thread checks while it's performing work.
     */
    private volatile boolean closed = false; // TODO: should this be AtomicBoolean?

    /**
     * Separate from closed to allow aborting even while already closed or closing
     * This happens when an error occurs while processing remainder of queue, it's
     * also useful to allow aborting when closing unexpectedly takes a long time.
     */
    private boolean aborted = false;

    public FileProcessor(final int numberOfThreadsToUse, boolean recurseSubdirs, boolean processArchives) {
        this.recurseSubdirs = recurseSubdirs;
        this.processArchives = processArchives;
        setFileNameGlob("*");

        // We always use an ExecutorService to call our handlers to simplify our code
        // When not using threads, the service is just a fancy wrapper around doing task.run() directly inside the calling thread.
        if (numberOfThreadsToUse > 1) {
            // NOTE: we need to create our own executor instead of using Executors.newFixedThreadPool()
            // Because that implementation has an unbounded job queue by default, we run the risk that we queue 50k jobs and eat up all memory
            // So we provide our own queue that will block when it's full.
            // We could just provide a default LinkedBlockingDeque, but the default behavior for the executor is to reject all jobs while the queue is full.
            // To get around this, override the queue.offer() function used internally by the executor to queue jobs,
            // and make it blocking (instead of returning false instantly, which would make the executor reject the job)
            int cpuCores = Runtime.getRuntime().availableProcessors();
            int actualThreadsToUse = Math.max(1, Math.min(cpuCores - 1, numberOfThreadsToUse)); // no more than (cores-1), but at least 1
            executor = new ThreadPoolExecutor(actualThreadsToUse, actualThreadsToUse, Integer.MAX_VALUE, TimeUnit.DAYS,
                // We don't need a long queue at all
                // Every queued job holds a full document in memory, and documents can be *very* large (100Meg+)
                new LinkedBlockingDeque<Runnable>(Math.max(1, actualThreadsToUse / 2)) {
                    @Override
                    public boolean offer(Runnable r) {
                        try {
                            return offer(r, Integer.MAX_VALUE, TimeUnit.DAYS);
                        } catch (InterruptedException e) {
                            return false;
                        }
                    }
                }
            );

            // Never throw RejectedExecutionException in the main thread
            // (this can rarely happen when the FileProcessor shut down from another thread (usually a task thread that encountered an exception?)
            // just in between checking state and submitting)
            ((ThreadPoolExecutor) executor).setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        } else {
            executor = new MainThreadExecutorService((r, e) -> {
                /* swallow RejectedExecutionExceptions, same as above. */ });
        }
    }

    /**
     * Only process files matching the glob. NOTE: this pattern is NOT applied to
     * directories.
     *
     * @param glob
     */
    public void setFileNameGlob(String glob) {
        pattGlob = Pattern.compile(FileUtil.globToRegex(glob == null ? "*" : glob));
    }

    /**
     * Only process files matching the pattern. NOTE: this pattern is NOT applied to
     * directories.
     *
     * @param pattGlob
     */
    public void setFileNamePattern(Pattern pattGlob) {
        this.pattGlob = pattGlob;
    }

    /**
     * The pattern to filter files before they are processed. NOTE: this pattern is
     * NOT applied to directories.
     *
     * @return the pattern
     */
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

    public synchronized void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void setFileHandler(FileHandler fileHandler) {
        this.fileHandler = fileHandler;
    }

    /**
     * Process a file or directory.
     *
     * If this file is a directory, all child files will be processed, files within
     * subdirectories will only be processed if {@link #isRecurseSubdirs()} is true.
     * For rules on how files are processed, regarding archives etc, see
     * {@link #processInputStream(String, InputStream, File)}.
     *
     * @param file file, directory or archive to process
     */
    public void processFile(File file) {
        if (!file.exists()) {
            reportAndAbort(new FileNotFoundException("Input file or dir not found: " + file), file.getAbsolutePath(), file);
            return;
        }

        if (closed)
            return;

        if (file.isDirectory()) { // Even if recurseSubdirs is false, we should process all direct children
            for (File childFile : FileUtil.listFilesSorted(file)) {
                if (closed)
                    return;

                // Report
                if (childFile.isDirectory()) {
                    CompletableFuture.runAsync(makeRunnable(() -> fileHandler.directory(childFile)), executor)
                            .exceptionally(e -> reportAndAbort(e, childFile.toString(), childFile));
                }

                if (recurseSubdirs || !childFile.isDirectory())
                    processFile(childFile);
            }
        } else {
            try {
                processFile(file.getAbsolutePath(), FileUtils.readFileToByteArray(file), file);
            } catch (IOException e) {
                reportAndAbort(e, file.getAbsolutePath(), file);
                return;
            }
        }
    }

    /**
     * Process from an InputStream, which may be an archive or a regular file.
     *
     * Archives (.zip and .tar.gz) will only be processed if
     * {@link #isProcessArchives()} is true. GZipped files (.gz) will be unpacked
     * regardless. Note that all files within archives will be processed, regardless
     * of whether they match {@link FileProcessor#pattGlob}
     *
     * @param path filename, optionally including path to the file or path within an
     *            archive
     * @param is the stream
     * @param file (optional) the file from which the InputStream was built, or - if
     *            the InputStream is a file within an archive - the archive. This is
     *            only used for reporting to FileHandler and ErrorHandler
     */
    public void processInputStream(String path, InputStream is, File file) {
        if (closed)
            return;

        TarGzipReader.FileHandler handler = (pathInArchive, bytes) -> {
            processFile(pathInArchive, bytes, file);
            return !closed; // quit processing the archive if we've received an error in the meantime
        };

        if (isProcessArchives() && path.endsWith(".tar.gz") || path.endsWith(".tgz")) {
            TarGzipReader.processTarGzip(path, is, handler);
        } else if (isProcessArchives() && path.endsWith(".zip")) {
            TarGzipReader.processZip(path, is, handler);
        } else if (path.endsWith(".gz")) {
            TarGzipReader.processGzip(path, is, handler);
        } else if (!skipFile(path) && getFileNamePattern().matcher(path).matches()) {
            CompletableFuture.runAsync(makeRunnable(() -> fileHandler.file(path, is, file)), executor)
                    .exceptionally(e -> reportAndAbort(e, path, file));
        }
    }

    /**
     * Process from a raw file content array, which may be an archive or a regular file.
     *
     * Archives (.zip and .tar.gz) will only be processed if
     * {@link #isProcessArchives()} is true. GZipped files (.gz) will be unpacked
     * regardless. Note that all files within archives will be processed, regardless
     * of whether they match {@link FileProcessor#pattGlob}
     *
     * @param path filename, optionally including path to the file or path within an
     *            archive
     * @param contents
     * @param file (optional) the file backing the contents, or - if
     *            the contents array represents a file within an archive - the archive. This is
     *            only used for reporting to FileHandler and ErrorHandler
     */
    public void processFile(String path, byte[] contents, File file) {
        if (closed)
            return;

        TarGzipReader.FileHandler handler = (pathInArchive, decodedBytes) -> {
            processFile(pathInArchive, decodedBytes, file);
            return !closed; // quit processing the archive if we've received an error in the meantime
        };

        if (isProcessArchives() && path.endsWith(".tar.gz") || path.endsWith(".tgz")) {
            TarGzipReader.processTarGzip(path, new ByteArrayInputStream(contents), handler);
        } else if (isProcessArchives() && path.endsWith(".zip")) {
            TarGzipReader.processZip(path, new ByteArrayInputStream(contents), handler);
        } else if (path.endsWith(".gz")) {
            TarGzipReader.processGzip(path, new ByteArrayInputStream(contents), handler);
        } else if (!skipFile(path) && getFileNamePattern().matcher(path).matches()) {
            CompletableFuture.runAsync(makeRunnable(() -> fileHandler.file(path, contents, file)), executor)
                    .exceptionally(e -> reportAndAbort(e, path, file));
        }
    }

    /**
     * Callback for when handler throws an exception. Report it, and if it's
     * irrecoverable, abort.
     * {@link ErrorHandler#errorOccurred(Throwable, String, File)}
     *
     * @param e
     * @param path
     * @param f
     * @return always null, has return type to enable use as exception handler in
     *         CompletableFuture
     */
    private synchronized Void reportAndAbort(Throwable e, String path, File f) {
        if (e instanceof CompletionException) // async exception
            e = e.getCause();

        // Only report the first fatal exception
        if (!aborted) {
            if (errorHandler == null) {
                System.err.println("WARNING: No errorHandler set for FileProcessor!");
                e.printStackTrace(System.err);
            }
            if (errorHandler == null || !errorHandler.errorOccurred(e, path, f)) {
                abort();
            }
        }

        return null;
    }

    /**
     * Like {@link FileProcessor#close()} but immediately abort all running handler
     * tasks and cancel any pending tasks.
     *
     * Subsequent calls to close, processFile or processInputStream will have no
     * effect.
     */
    // this function can't be synchronized on (this) or we couldn't abort from an async handler while the main thread is working/waiting on close().
    public void abort() {
        synchronized (this) {
            if (aborted)
                return;
            closed = true;
            aborted = true;
        }

        executor.shutdownNow();
    }

    /**
     * Close the executor and wait until all running and pending handler tasks have
     * completed. Calling close() while processFile or processInputStream is in
     * progress will cause them to skip all remaining files. Files for which a task
     * has already been put in the queue will still be processed as normal.
     *
     * Subsequent calls to close, processFile or processInputStream will have no
     * effect.
     */
    @Override
    public void close() {
        synchronized (this) {
            if (closed)
                return;
            closed = true;
        }

        try {
            executor.shutdown();
            // Outside the synchronized block to allow calling abort() while waiting for close() to complete
            // This is used by tasks that threw a fatal exception
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            throw new BlackLabRuntimeException("Interrupted while waiting for processing threads to finish", e);
        }
    }

    /*
     * Bit of boilerplate to allow using submitting tasks that throw checked exceptions with CompletableFuture.
     * Wrap the task in a runnable that catches the checked exception and rethrows it in an unchecked manner.
     * The exception is then caught in the future and made available (using for example CompletableFuture::completeExceptionally)
     *
     * see https://blog.jooq.org/2012/09/14/throw-checked-exceptions-like-runtime-exceptions-in-java/
     */
    @SuppressWarnings("unchecked")
    private static <T extends Exception> void rethrowUnchecked(Exception t) throws T {
        throw (T) t;
    }

    @FunctionalInterface
    private interface ThrowingRunnable<E extends Throwable> {
        void call() throws E;
    }

    private static <E extends Exception> Runnable makeRunnable(ThrowingRunnable<E> c) {
        return () -> {
            try {
                c.call();
            } catch (Exception e) {
                rethrowUnchecked(e);
            }
        };
    }
}
