package nl.inl.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Process (trees of) files, which may include archives
 * that we want to recursively process as well.
 */
public class FileProcessor {

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

    /** Restrict the files we handle to a file glob? */
    private Pattern pattGlob;

    /** Process subdirectories? */
    private boolean recurseSubdirs;

    /** Process archives as directories?
     *  Note that this setting is independent of recurseSubdirs; if this is true,
     *  files inside archives will be processed, even if recurseSubdirs is false.
     */
    private boolean processArchives;

    /** Skip files like Thumbs.db (Windows) and .DS_Store (OSX)? */
    private boolean skipOsSpecialFiles = true;

    /** What to do with each file */
    private FileHandler fileHandler;

    /** Decides whether or not to continue when an error occurs */
    private ErrorHandler errorHandler = new SimpleErrorHandler(false);

    /** If false, we shouldn't process any more files */
    boolean keepProcessing;

    public FileProcessor() {
        this(true, true);
        setFileNameGlob("*");
        reset();
    }

    public void reset() {
        keepProcessing = true;
    }

    public FileProcessor(boolean recurseSubdirs, boolean processArchives) {
        this.recurseSubdirs = recurseSubdirs;
        this.processArchives = processArchives;
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
        String fn = fileToIndex.getCanonicalPath(); //Name();
        if (fileToIndex.isDirectory()) {
            indexDir(fileToIndex);
        } else {
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
    private void indexDir(File dir) throws UnsupportedEncodingException,
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
    private void indexZip(File zipFile) throws Exception {
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
                            if (isArchive) {
                                if (processArchives)
                                    processInputStream(completePath, is);
                            } else {
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
                int i = filePath.lastIndexOf("/");
                String fileName = i < 0 ? filePath : filePath.substring(i + 1);
                if (!skipFile(fileName)) {
                    String completePath = tgzFileName + "/" + filePath;
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
