package nl.inl.blacklab.interfaces.index;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.index.IndexListener;
import nl.inl.blacklab.interfaces.struct.IndexMetadataWriter;

/**
 * Interface for writing to a BlackLab index.
 */
interface BlackLabIndexWriter extends Closeable {
    
    /**
     * Get the Lucene index writer.
     * @return Lucene index writer
     */
    IndexWriter writer();
    
    /**
     * Get our index metadata.
     * @return index metadata
     */
    IndexMetadataWriter metadata();
    
    /**
     * Is this a newly created, empty index?
     * @return true if it is, false if not
     */
    boolean isEmpty();
    
    /**
     * Call this to roll back any changes made to the index this session.
     * Calling {@link #close() close()} will automatically commit any changes. If you call this
     * method, then call close(), no changes will be committed.
     */
    void rollback();
    
    /** Deletes documents matching a query from the BlackLab index.
     *
     * This deletes the documents from the Lucene index, the forward indices and the content store(s).
     * @param q the query
     */
    void delete(Query q);
    
    /**
     * Get the analyzer for indexing and searching.
     * @return the analyzer
     */
    Analyzer analyzer();
    
    /**
     * Set the listener object that receives messages about indexing progress.
     * @param listener the listener object to report to
     */
    void setListener(IndexListener listener);

    /**
     * Index a document or archive from an InputStream.
     *
     * @param documentName
     *            name for the InputStream (e.g. name of the file)
     * @param input
     *            the stream
     */
    void index(String documentName, InputStream input);
    
    /**
     * Index a document from a Reader.
     *
     * NOTE: it is generally better to supply an (UTF-8) InputStream or byte array directly,
     * as this can in some cases be parsed more efficiently (e.g. using VTD-XML).
     *
     * Catches and reports any errors that occur.
     *
     * @param documentName
     *            some (preferably unique) name for this document (for example, the file
     *            name or path)
     * @param reader
     *            where to index from
     * @throws Exception
     */
    void index(String documentName, Reader reader);

    /**
     * Index a document (or archive if enabled by {@link #setProcessArchivesAsDirectories(boolean)}
     *
     * @param fileName
     * @param input
     * @param fileNameGlob
     */
    void index(String fileName, InputStream input, String fileNameGlob);

    /**
     * Index the file or directory specified.

     * Indexes all files in a directory or archive (previously
     * only indexed *.xml; specify a glob if you want this
     * behaviour back, see {@link #index(File, String)}.
     *
     * Recurses into subdirs only if that setting is enabled.
     *
     * @param file
     *                 the input file or directory
     */
    void index(File file);

    /**
     * Index a document, archive (if enabled by {@link #setProcessArchivesAsDirectories(boolean)}, or directory, optionally recursively if set by {@link #setRecurseSubdirs(boolean)}
     *
     * @param file
     * @param fileNameGlob only files
     */
    // TODO this is nearly a literal copy of index for a stream, unify them somehow (take care that file might be a directory)
    void index(File file, String fileNameGlob);

    /**
     * Set parameters we would like to be passed to the DocIndexer class
     * @param indexerParam the parameters
     */
    void setIndexerParam(Map<String, String> indexerParam);

    /**
     * Set the directories to search for linked files.
     *
     * DocIndexerXPath allows us to index a second file into the
     * same Lucene document, which is useful for external metadata, etc.
     * This determines how linked files are located.
     *
     * @param linkedFileDirs directories to search
     */
    void setLinkedFileDirs(List<File> linkedFileDirs);

    /**
     * Add a directory to search for linked files.
     *
     * DocIndexerXPath allows us to index a second file into the
     * same Lucene document, which is useful for external metadata, etc.
     * This determines how linked files are located.
     *
     * @param linkedFileDir directory to search
     */
    void addLinkedFileDir(File linkedFileDir);

    void setLinkedFileResolver(Function<String, File> resolver);

    void setUseThreads(boolean useThreads);

    
}
