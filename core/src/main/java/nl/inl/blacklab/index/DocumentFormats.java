package nl.inl.blacklab.index;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.index.config.InputFormatConfigException;

/**
 * Document format registry, for resolving a DocIndexer class given a
 * format identifier (common abbreviation or (qualified) class name).
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

		registerFactory(new DocIndexerFactoryConvertAndTag());
    }

    /**
     * Register a new factory.
     * Factories are responsible for creating the docIndexer instances that will perform the indexing of documents.
     * newer factories have priority over older factories in case of name conflicts.
     *
     * If an exception occurs during initialization of the factory, the factory will not be added, and the exception is rethrown.
     * @param fac
     * @throws RuntimeException rethrows exceptions occuring during factory initialization
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
    public static void registerFormat(String formatIdentifier, Class<? extends DocIndexer> docIndexerClass) {
    	builtinClassFactory.addFormat(formatIdentifier, docIndexerClass);
    }

    // Convenience method to avoid applications always having to create a DocIndexerFactory instance
    public static void registerFormat(ConfigInputFormat config) {
    	builtinConfigFactory.addFormat(config);
    }

    // Convenience method to avoid applications always having to create a DocIndexerFactory instance
    public static void registerFormatsInDirectories(List<File> dirs) throws InputFormatConfigException {
    	builtinConfigFactory.addFormatsInDirectories(dirs);
    }

    /**
     * Returns the factory which will be used to create the DocIndexer registered under this formatIdentifier.
     * This method isn't used in BlackLab itself, but it could be useful for client applications.
     *
     * @param formatIdentifier
     * @return the factory if a valid formatIdentifier is provided, null otherwise
     */
    public static DocIndexerFactory getFactory(String formatIdentifier) {
        if (formatIdentifier == null || formatIdentifier.isEmpty())
        	return null;

    	for (int i = factories.size()-1; i >= 0; i--) {
    		if (factories.get(i).isSupported(formatIdentifier))
    			return factories.get(i);
    	}
    	return null;
	}

    /**
	 * Check if a particular string denotes a valid document format.
	 *
	 * @param formatIdentifier format identifier, e.g. "tei" or "com.example.MyIndexer"
	 * @return true iff a registered factory supports this format
	 */
	public static boolean isSupported(String formatIdentifier) {
		return getFactory(formatIdentifier) != null;
	}

	/**
	 * Returns an alphabetically sorted list of registered document formats.
	 * Note that though this list will not contain duplicates, duplicates might exist internally (see {@link DocumentFormats#getFormats()}).
	 *
	 * @return the list of registered formatIdentifiers
	 * @deprecated use getFormats()
	 */
    @Deprecated
	public static List<String> list() {
		HashSet<String> s = new HashSet<>();
		for (Format d : getFormats())
			s.add(d.getId());

		List<String> l = new ArrayList<>(s);
		Collections.sort(l);
		return l;
	}

    /**
     * Returns a list of all registered document formats for all factories,
     * ordered by descending priority.
     * Note that this list might contain duplicates if multiple factories support the same formatIdentifier,
     * such as when registering extra instances of the builtin factories.
     * In this case, the first of those entries is the one that is actually used.
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
	 * @param formatIdentifier
	 * @return the descriptor, or null if not supported by any factory
	 */
	public static Format getFormat(String formatIdentifier) {
		DocIndexerFactory factory = getFactory(formatIdentifier);
		return factory != null ? factory.getFormat(formatIdentifier) : null;
	}

	// Convenience
	public static DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, Reader reader)
			throws UnsupportedOperationException {
		DocIndexerFactory fac = getFactory(formatIdentifier);
		return fac != null ? fac.get(formatIdentifier, indexer, documentName, reader) : null;
	}

	// Convenience
	public static DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, InputStream is, Charset cs)
			throws UnsupportedOperationException {
		DocIndexerFactory fac = getFactory(formatIdentifier);
		return fac != null ? fac.get(formatIdentifier, indexer, documentName, is, cs) : null;
	}

	// Convenience
	public static DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, File f, Charset cs)
			throws UnsupportedOperationException, FileNotFoundException {
		DocIndexerFactory fac = getFactory(formatIdentifier);
		return fac != null ? fac.get(formatIdentifier, indexer, documentName, f, cs) : null;
	}

	// Convenience
	public static DocIndexer get(String formatIdentifier, Indexer indexer, String documentName, byte[] b, Charset cs)
			throws UnsupportedOperationException {
		DocIndexerFactory fac = getFactory(formatIdentifier);
		return fac != null ? fac.get(formatIdentifier, indexer, documentName, b, cs) : null;
	}
}
