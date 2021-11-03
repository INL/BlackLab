package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;

import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;

public interface Indexer {

    static Indexer createNewIndex(File directory) throws DocumentFormatNotFound, ErrorOpeningIndex {
        return new IndexerImpl(directory, true);
    }

    static Indexer createNewIndex(File directory, String formatIdentifier) throws DocumentFormatNotFound, ErrorOpeningIndex {
        return new IndexerImpl(directory, true, formatIdentifier, null);
    }

    static Indexer openIndex(File directory) throws DocumentFormatNotFound, ErrorOpeningIndex {
        return new IndexerImpl(directory, false);
    }

    static Indexer openIndex(File directory, String formatIdentifier) throws DocumentFormatNotFound, ErrorOpeningIndex {
        return new IndexerImpl(directory, false, formatIdentifier, null);
    }

    static Indexer openIndex(BlackLabIndexWriter writer, String formatIdentifier) throws DocumentFormatNotFound {
        return new IndexerImpl(writer, formatIdentifier);
    }

    static Indexer openIndex(File directory, boolean createNewIndex, String formatIdentifier) throws DocumentFormatNotFound, ErrorOpeningIndex {
        return new IndexerImpl(directory, createNewIndex, formatIdentifier, null);
    }

    static Indexer openIndex(File directory, boolean createNewIndex, String formatIdentifier, File indexTemplateFile) throws DocumentFormatNotFound, ErrorOpeningIndex {
        return new IndexerImpl(directory, createNewIndex, formatIdentifier, indexTemplateFile);
    }

    Charset DEFAULT_INPUT_ENCODING = StandardCharsets.UTF_8;
    /** Annotated field name for default contents field */
    String DEFAULT_CONTENTS_FIELD_NAME = "contents";

    /**
     * When we encounter a zip or tgz file, do we descend into it like it was a
     * directory?
     *
     * Note that for accessing large ZIP files, you need Java 7 which supports the
     * ZIP64 format, otherwise you'll get the "invalid CEN header (bad signature)"
     * error.
     *
     * @param b if true, treats zipfiles like a directory and processes all the
     *            files inside
     */
    void setProcessArchivesAsDirectories(boolean b);

    /**
     * Should we recursively index files in subdirectories (and archives files, if
     * that setting is on)?
     *
     * @param recurseSubdirs true if we should recurse into subdirs
     */
    void setRecurseSubdirs(boolean recurseSubdirs);

    void setFormatIdentifier(String formatIdentifier) throws DocumentFormatNotFound;

    /**
     * Set the listener object that receives messages about indexing progress.
     *
     * @param listener the listener object to report to
     */
    void setListener(IndexListener listener);

    /**
     * Get our index listener, or create a console reporting listener if none was
     * set yet.
     *
     * Also reports the creation of the Indexer and start of indexing, if it hadn't
     * been reported already.
     *
     * @return the listener
     */
    IndexListener listener();

    /**
     * Set number of documents after which we should stop. Useful when testing.
     *
     * @param n number of documents after which to stop
     */
    void setMaxNumberOfDocsToIndex(int n);

    /**
     * Call this to roll back any changes made to the index this session. Calling
     * close() will automatically commit any changes. If you call this method, then
     * call close(), no changes will be committed.
     */
    void rollback();

    /**
     * Close the index
     */
    // TODO this should call close() on running FileProcessors
    void close();

    /**
     * @return true iff indexer is closed
     * @deprecated changed to isOpen to match Lucene
     */
    @Deprecated
    boolean isClosed();

    /**
     * Is this indexer open?
     *
     * An indexer can be closed unexpectedly if e.g. the GC overhead limit
     * is exceeded.
     *
     * @return true if the indexer is open, false if not
     */
    boolean isOpen();

    /**
     * Updates the specified Document in the index.
     *
     * @param term how to find the document to update
     * @param document the updated document
     * @throws IOException
     */
    void update(Term term, Document document) throws IOException;

    /**
     * Index a document or archive from an InputStream.
     *
     * @param documentName name for the InputStream (e.g. name of the file)
     * @param input the stream
     */
    void index(String documentName, InputStream input);

    /**
     * @deprecated (since 2.2)
     * Index a document from a Reader.
     *
     * NOTE: it is generally better to supply an (UTF-8) InputStream or byte array
     * directly, as this can in some cases be parsed more efficiently (e.g. using
     * VTD-XML).
     *
     * Catches and reports any errors that occur to the IndexListener.
     *
     * @param documentName some (preferably unique) name for this document (for
     *            example, the file name or path)
     * @param reader where to index from
     *
     */
    @Deprecated
    void index(String documentName, Reader reader);

    /**
     * Index a document (or archive if enabled by
     * {@link #setProcessArchivesAsDirectories(boolean)}
     * 
     * Catches and reports any errors that occur to the IndexListener.  
     *
     * @param fileName
     * @param input
     * @param fileNameGlob
     * Only used if this file is a directory or is determined to be an archive. Only process files matching the glob.
     */
    void index(String fileName, InputStream input, String fileNameGlob);

    /**
     * Index the file or directory specified.
     *
     * Indexes all files in a directory or archive (previously only indexed *.xml;
     * specify a glob if you want this behaviour back, see
     * {@link #index(File, String)}.
     *
     * Recurses into subdirs only if that setting is enabled.
     *
     * @param file the input file or directory
     */
    default void index(File file) { index(file, null); }

    /**
     * Index a document, archive (if enabled by
     * {@link #setProcessArchivesAsDirectories(boolean)}, or directory, optionally
     * recursively if set by {@link #setRecurseSubdirs(boolean)}
     *
     * @param file
     * @param fileNameGlob 
     * Only used if this file is a directory or is determined to be an archive. Only process files matching the glob.
     */
    void index(File file, String fileNameGlob);
    
    /** 
     * Index a file or archive of files from memory.
     * Encoding is guessed based on file contents.
     * 
     * @param fileName name of the file including extension. Used to detect archives/file types.
     * @param contents file contents
     * @param fileNameGlob 
     * Only used if this file is a directory or is determined to be an archive. Only process files matching the glob.     */
    void index(String fileName, byte[] contents, String fileNameGlob);
    
    /** 
     * Index a file or archive of files from memory.
     * 
     * @param fileName name of the file including extension. Used to detect archives/file types.
     * @param contents file contents 
     */
    default void index(String fileName, byte[] contents) { index(fileName, contents, null); }
    
    /**
     * Get our index directory
     *
     * @return the index directory
     */
    File indexLocation();

    /**
     * The index we're writing to.
     *
     * @return index writer
     */
    BlackLabIndexWriter indexWriter();

    /**
     * Set parameters we would like to be passed to the DocIndexer class
     *
     * @param indexerParam the parameters
     */
    void setIndexerParam(Map<String, String> indexerParam);

    /**
     * Set the directories to search for linked files.
     *
     * DocIndexerXPath allows us to index a second file into the same Lucene
     * document, which is useful for external metadata, etc. This determines how
     * linked files are located.
     *
     * @param linkedFileDirs directories to search
     */
    void setLinkedFileDirs(List<File> linkedFileDirs);

    void setLinkedFileResolver(Function<String, File> resolver);

    @Deprecated
    void setUseThreads(boolean useThreads);

    void setNumberOfThreadsToUse(int numberOfThreadsToUse);

}
