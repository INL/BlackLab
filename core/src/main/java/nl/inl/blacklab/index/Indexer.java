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

import nl.inl.blacklab.exceptions.DocumentFormatException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.search.BlackLabIndexWriter;

public interface Indexer {

    static Indexer createNewIndex(File directory) throws DocumentFormatException, ErrorOpeningIndex {
        return new IndexerImpl(directory, true);
    }
    
    static Indexer createNewIndex(File directory, String formatIdentifier) throws DocumentFormatException, ErrorOpeningIndex {
        return new IndexerImpl(directory, true, formatIdentifier, null);
    }

    static Indexer openIndex(File directory) throws DocumentFormatException, ErrorOpeningIndex {
        return new IndexerImpl(directory, false);
    }
    
    static Indexer openIndex(File directory, String formatIdentifier) throws DocumentFormatException, ErrorOpeningIndex {
        return new IndexerImpl(directory, false, formatIdentifier, null);
    }

    static Indexer openIndex(File directory, boolean createNewIndex, String formatIdentifier) throws DocumentFormatException, ErrorOpeningIndex {
        return new IndexerImpl(directory, createNewIndex, formatIdentifier, null);
    }

    static Indexer openIndex(File directory, boolean createNewIndex, String formatIdentifier, File indexTemplateFile) throws DocumentFormatException, ErrorOpeningIndex {
        return new IndexerImpl(directory, createNewIndex, formatIdentifier, indexTemplateFile);
    }

    Charset DEFAULT_INPUT_ENCODING = StandardCharsets.UTF_8;

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

    void setFormatIdentifier(String formatIdentifier) throws DocumentFormatException;

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

    boolean isClosed();

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
     * Index a document from a Reader.
     *
     * NOTE: it is generally better to supply an (UTF-8) InputStream or byte array
     * directly, as this can in some cases be parsed more efficiently (e.g. using
     * VTD-XML).
     *
     * Catches and reports any errors that occur.
     *
     * @param documentName some (preferably unique) name for this document (for
     *            example, the file name or path)
     * @param reader where to index from
     * 
     * @throws IOException if an I/O error occurred
     * @throws MalformedInputFile if the input file was invalid
     * @throws PluginException if an error in a plugin occurred 
     */
    void index(String documentName, Reader reader) throws IOException, MalformedInputFile, PluginException;

    /**
     * Index a document (or archive if enabled by
     * {@link #setProcessArchivesAsDirectories(boolean)}
     *
     * @param fileName
     * @param input
     * @param fileNameGlob
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
    void index(File file);

    /**
     * Index a document, archive (if enabled by
     * {@link #setProcessArchivesAsDirectories(boolean)}, or directory, optionally
     * recursively if set by {@link #setRecurseSubdirs(boolean)}
     *
     * @param file
     * @param fileNameGlob only files
     */
    // TODO this is nearly a literal copy of index for a stream, unify them somehow (take care that file might be a directory)
    void index(File file, String fileNameGlob);

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

    void setUseThreads(boolean useThreads);

}
