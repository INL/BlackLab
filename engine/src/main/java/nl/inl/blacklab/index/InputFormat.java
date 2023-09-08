package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;

import nl.inl.blacklab.indexers.config.ConfigInputFormat;

/**
 * Description of a supported input format
 */
public interface InputFormat {

    /**
     * Convenience function.
     * <p>
     * Note that it is usually faster to offer the document as byte[] and charset,
     * because that allows the buffer to be passed on without copying.
     *
     * @param indexer our indexer
     * @param documentName document name
     * @param is contents
     * @param cs file encoding
     * @return the DocIndexer
     */
    DocIndexer createDocIndexer(DocWriter indexer, String documentName, InputStream is,
            Charset cs)
            throws UnsupportedOperationException;

    /** Convenience function.
     *
     * @param indexer our indexer
     * @param documentName document name
     * @param f file
     * @param cs file encoding
     * @return the DocIndexer
     */
    DocIndexer createDocIndexer(DocWriter indexer, String documentName, File f, Charset cs)
            throws UnsupportedOperationException, FileNotFoundException;

    /** Convenience function.
     *
     * @param indexer our indexer
     * @param documentName document name
     * @param b file contents
     * @param cs file encoding
     * @return the DocIndexer, or null if format not found
     */
    DocIndexer createDocIndexer(DocWriter indexer, String documentName, byte[] b, Charset cs)
            throws UnsupportedOperationException;

    String getIdentifier();

    String getDisplayName();

    String getDescription();

    String getHelpUrl();

    boolean isVisible();

    default boolean isError() { return getErrorMessage() != null; }

    default String getErrorMessage() {
        return null;
    }

    default boolean isConfigurationBased() { return getConfig() != null; }

    default ConfigInputFormat getConfig() { return null; }

}
