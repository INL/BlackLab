package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;

public interface DocIndexer extends AutoCloseable {
    int MAX_DOCVALUES_LENGTH = Short.MAX_VALUE - 100; // really - 1, but let's be extra safe

    @Override
    void close();

    Document getCurrentLuceneDoc();

    /**
     * Returns our DocWriter object
     *
     * @return the DocWriter object
     */
    DocWriter getDocWriter();

    /**
     * Set the DocWriter object.
     *
     * We use this to add documents to the index.
     *
     * Called by Indexer when the DocIndexer is instantiated.
     *
     * @param docWriter our DocWriter object
     */
    void setDocWriter(DocWriter docWriter);

    /**
     * Set the file name of the document to index.
     *
     * @param documentName name of the document
     */
    void setDocumentName(String documentName);

    /**
     * Set the document to index.
     *
     * @param is document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    void setDocument(InputStream is, Charset cs);

    /**
     * Set the document to index.
     *
     * @param contents document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    void setDocument(byte[] contents, Charset cs);

    /**
     * Set the document to index.
     *
     * @param file file to index
     * @param charset charset to use if no BOM found, or null for the default
     *         (utf-8)
     * @throws FileNotFoundException if not found
     */
    void setDocument(File file, Charset charset) throws FileNotFoundException;

    /**
     * Index documents contained in a file.
     *
     * @throws MalformedInputFile if the input file wasn't valid
     * @throws IOException if an I/O error occurred
     * @throws PluginException if an error occurred in a plugin
     */
    void index() throws IOException, MalformedInputFile, PluginException;

    /**
     * Enables or disables norms. Norms are disabled by default.
     *
     * The method name was chosen to match Lucene's Field.setOmitNorms(). Norms are
     * only required if you want to use document-length-normalized scoring.
     *
     * @param b if true, doesn't store norms; if false, does store norms
     */
    void setOmitNorms(boolean b);

    void addNumericFields(Collection<String> fields);

    boolean continueIndexing();

    List<String> getMetadataField(String name);

    void addMetadataField(String name, String value);

    /**
     * When all metadata values have been set, call this to add the to the Lucene document.
     *
     * We do it this way because we don't want to add multiple values for a field (DocValues and
     * Document.get() only deal with the first value added), and we want to set an "unknown value"
     * in certain conditions, depending on the configuration.
     */
    void addMetadataToDocument();

    /**
     * Report the amount of new characters processed since the last call
     */
    void reportCharsProcessed();

    /**
     * Report the amounf of new tokens processed since the last call
     */
    void reportTokensProcessed();

    /**
     * Keep track of how many tokens have been processed.
     */
    void documentDone(String documentName);

    /**
     * Keep track of how many tokens have been processed.
     */
    void tokensDone(int n);

    int numberOfDocsDone();

    long numberOfTokensDone();
}
