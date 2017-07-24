package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

import nl.inl.blacklab.index.xpath.ConfigInputFormat;

public interface DocIndexerFactory {

    /**
     * Instantiating a DocIndexer from a reader.
     *
     * @param indexer indexer object
     * @param documentName name of the unit we're indexing
     * @param reader text to index
     * @return DocIndexer instance
     */
    DocIndexer get(Indexer indexer, String documentName, Reader reader);

    /**
     * Instantiating a DocIndexer from an input stream.
     *
     * @param indexer indexer object
     * @param documentName name of the unit we're indexing
     * @param is data to index
     * @param cs default character set if not defined
     * @return DocIndexer instance
     */
    DocIndexer get(Indexer indexer, String documentName, InputStream is, Charset cs);

    /**
     * Instantiating a DocIndexer from a file.
     *
     * @param indexer indexer object
     * @param documentName name of the unit we're indexing
     * @param f file to index
     * @param cs default character set if not defined
     * @return DocIndexer instance
     * @throws FileNotFoundException if file doesn't exist
     */
    DocIndexer get(Indexer indexer, String documentName, File f, Charset cs) throws FileNotFoundException;

    /**
     * Instantiating a DocIndexer from a byte array.
     *
     * @param indexer indexer object
     * @param documentName name of the unit we're indexing
     * @param b data to index
     * @param cs default character set if not defined
     * @return DocIndexer instance
     */
    DocIndexer get(Indexer indexer, String documentName, byte[] b, Charset cs);

    /**
     * Return this DocIndexerFactory's input format configuration, if it has one.
     * @return the input format configuration, or null if it has none
     */
    ConfigInputFormat getConfig();

}
