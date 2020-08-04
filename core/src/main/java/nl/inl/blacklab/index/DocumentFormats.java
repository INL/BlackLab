package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;

/**
 * Document format registry, for resolving a DocIndexer class given a format
 * identifier (common abbreviation or (qualified) class name).
 */
public class DocumentFormats {

    private static final List<DocIndexerFactory> factories = new ArrayList<>();

    // We keep a handle to these factories to allow programs on top of blacklab to easily add
    // new configs and DocIndexer classes through the DocumentFormats class instead of
    // requiring them to always register a new factory for the format
    private static final DocIndexerFactoryClass builtinClassFactory;
    private static final DocIndexerFactoryConfig builtinConfigFactory;

    static {
        builtinClassFactory = new DocIndexerFactoryClass();
        builtinConfigFactory = new DocIndexerFactoryConfig();

        // Last registered factory has priority (to allow users to override types)
        // So register config-based factory after class-based factory
        registerFactory(builtinClassFactory);
        registerFactory(builtinConfigFactory);
    }

    /**
     * Register a new factory. Factories are responsible for creating the docIndexer
     * instances that will perform the indexing of documents. newer factories have
     * priority over older factories in case of name conflicts.
     *
     * If an exception occurs during initialization of the factory, the factory will
     * not be added, and the exception is rethrown.
     * 
     * @param fac
     * @throws RuntimeException rethrows exceptions occuring during factory
     *             initialization
     */
    public static void registerFactory(DocIndexerFactory fac) {
        if (factories.contains(fac))
            return;

        try {
            factories.add(fac);
            fac.init();
        } catch (Exception e) {
            factories.remove(fac);
            throw e;
        }
    }

    // Convenience method to avoid applications always having to create a DocIndexerFactory instance
    public static void registerFormat(String formatIdentifier, Class<? extends DocIndexerAbstract> docIndexerClass) {
        builtinClassFactory.addFormat(formatIdentifier, docIndexerClass);
    }

    // Convenience method to avoid applications always having to create a DocIndexerFactory instance
    public static void registerFormat(ConfigInputFormat config) {
        builtinConfigFactory.addFormat(config);
    }

    // Convenience method to avoid applications always having to create a DocIndexerFactory instance
    public static void registerFormatsInDirectories(List<File> dirs) throws InvalidInputFormatConfig {
        builtinConfigFactory.addFormatsInDirectories(dirs);
    }

    /**
     * Returns the factory which will be used to create the DocIndexer registered
     * under this formatIdentifier. This method isn't used in BlackLab itself, but
     * it could be useful for client applications.
     *
     * @param formatIdentifier
     * @return the factory if a valid formatIdentifier is provided, null otherwise
     */
    public static DocIndexerFactory getFactory(String formatIdentifier) {
        if (formatIdentifier == null || formatIdentifier.isEmpty())
            return null;

        for (int i = factories.size() - 1; i >= 0; i--) {
            if (factories.get(i).isSupported(formatIdentifier))
                return factories.get(i);
        }
        return null;
    }

    /**
     * Check if a particular string denotes a valid document format.
     *
     * @param formatIdentifier format identifier, e.g. "tei" or
     *            "com.example.MyIndexer"
     * @return true iff a registered factory supports this format
     */
    public static boolean isSupported(String formatIdentifier) {
        return getFactory(formatIdentifier) != null;
    }
    
    /**
     * If this format exists but has an error, return the error.
     * 
     * @param formatIdentifier format to check for errors
     * @return null if not found or no errors, the error otherwise  
     */
    public static String formatError(String formatIdentifier) {
        if (formatIdentifier == null || formatIdentifier.isEmpty())
            return null;

        for (int i = factories.size() - 1; i >= 0; i--) {
            if (factories.get(i).isSupported(formatIdentifier))
                return null;
            String result = factories.get(i).formatError(formatIdentifier);
            if (result != null)
                return result;
        }
        return null;
    }
    
    /**
     * Returns a list of all registered document formats for all factories, ordered
     * by descending priority. Note that this list might contain duplicates if
     * multiple factories support the same formatIdentifier, such as when
     * registering extra instances of the builtin factories. In this case, the first
     * of those entries is the one that is actually used.
     *
     * @return the list of registered abbreviations
     */
    public static List<Format> getFormats() {
        List<Format> ret = new ArrayList<>();
        for (DocIndexerFactory fac : factories)
            ret.addAll(fac.getFormats());
        Collections.reverse(ret);
        return ret;
    }

    /**
     * Returns a format descriptor for a specific format
     * 
     * @param formatIdentifier
     * @return the descriptor, or null if not supported by any factory
     */
    public static Format getFormat(String formatIdentifier) {
        DocIndexerFactory factory = getFactory(formatIdentifier);
        return factory != null ? factory.getFormat(formatIdentifier) : null;
    }

    /**
     * Convenience function.
     *  
     * Note that it is usually faster to offer the document as byte[] and charset, 
     * because that allows the buffer to be passed on without copying. 
     * 
     * @param formatIdentifier format to get the DocIndexer for
     * @param indexer our indexer
     * @param documentName document name
     * @param reader file contents
     * @return the DocIndexer
     * @throws UnsupportedOperationException 
     * @deprecated (since 2.2) use byte[] version 
     */
    @Deprecated
    public static DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, Reader reader)
            throws UnsupportedOperationException {
        DocIndexerFactory fac = getFactory(formatIdentifier);
        return fac != null ? fac.get(formatIdentifier, indexer, documentName, reader) : null;
    }

    /** 
     * Convenience function.
     * 
     * Note that it is usually faster to offer the document as byte[] and charset, 
     * because that allows the buffer to be passed on without copying.
     * 
     * @param formatIdentifier format to get the DocIndexer for
     * @param indexer our indexer
     * @param documentName document name
     * @param is contents
     * @param cs file encoding
     * @return the DocIndexer
     * @throws UnsupportedOperationException 
     */
    public static DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, InputStream is,
            Charset cs)
            throws UnsupportedOperationException {
        DocIndexerFactory fac = getFactory(formatIdentifier);
        return fac != null ? fac.get(formatIdentifier, indexer, documentName, is, cs) : null;
    }

    /** Convenience function.
     * 
     * @param formatIdentifier format to get the DocIndexer for
     * @param indexer our indexer
     * @param documentName document name
     * @param f file
     * @param cs file encoding
     * @return the DocIndexer
     * @throws UnsupportedOperationException 
     * @throws FileNotFoundException 
     */
    public static DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, File f, Charset cs)
            throws UnsupportedOperationException, FileNotFoundException {
        DocIndexerFactory fac = getFactory(formatIdentifier);
        return fac != null ? fac.get(formatIdentifier, indexer, documentName, f, cs) : null;
    }

    /** Convenience function.
     * 
     * @param formatIdentifier format to get the DocIndexer for
     * @param indexer our indexer
     * @param documentName document name
     * @param b file contents
     * @param cs file encoding
     * @return the DocIndexer
     * @throws UnsupportedOperationException 
     */
    public static DocIndexer get(String formatIdentifier, DocWriter indexer, String documentName, byte[] b, Charset cs)
            throws UnsupportedOperationException {
        DocIndexerFactory fac = getFactory(formatIdentifier);
        return fac != null ? fac.get(formatIdentifier, indexer, documentName, b, cs) : null;
    }
}
