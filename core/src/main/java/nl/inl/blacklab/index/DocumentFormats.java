package nl.inl.blacklab.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.index.config.InputFormatConfigException;
import nl.inl.blacklab.indexers.DocIndexerAlto;
import nl.inl.blacklab.indexers.DocIndexerFolia;
import nl.inl.blacklab.indexers.DocIndexerPageXml;
import nl.inl.blacklab.indexers.DocIndexerTei;
import nl.inl.blacklab.indexers.DocIndexerTeiPosInFunctionAttr;
import nl.inl.blacklab.indexers.DocIndexerTeiText;
import nl.inl.blacklab.indexers.DocIndexerWhiteLab2;
import nl.inl.blacklab.indexers.DocIndexerXmlSketch;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.FileUtil;
import nl.inl.util.FileUtil.FileTask;

/**
 * Document format registry, for resolving a DocIndexer class given a
 * format identifier (common abbreviation or (qualified) class name).
 */
public class DocumentFormats {

	/** Document formats */
	static Map<String, Class<? extends DocIndexer>> docIndexerClasses = new TreeMap<>();

    /** Configs for different document formats */
    static Map<String, ConfigInputFormat> formats = new TreeMap<>();

    /** How to find additional formats */
    static List<FormatFinder> formatFinders = new ArrayList<>();

    static {
        init();
    }

    protected static void init() {
        // Add some default format finders.
        // NOTE: Format finder that was added last is searched first
        formatFinders.add(new FormatFinderDocIndexerClass());   // last resort is to look for class directly
        //formatFinders.add(new FormatFinderConfigFileFromJar()); // load .blf.yaml config file from BlackLab JAR
        //formatFinders.add(new FormatFinderConfigDirs()); // load .blf.yaml/.blf.json config file from config dir

        // Some abbreviations for commonly used builtin DocIndexers.
        // You can also specify the classname for builtin DocIndexers,
        // or a fully-qualified name for your custom DocIndexer (must
        // be on the classpath)
        register("alto", DocIndexerAlto.class);
        register("di-folia", DocIndexerFolia.class);
        register("whitelab2", DocIndexerWhiteLab2.class);
        register("pagexml", DocIndexerPageXml.class);
        register("sketchxml", DocIndexerXmlSketch.class);

        // TEI has a number of variants
        // By default, the contents of the "body" element are indexed, but alternatively you can index the contents of "text".
        // By default, the "type" attribute is assumed to contain PoS, but alternatively you can use the "function" attribute.
        register("di-tei", DocIndexerTei.class);
        //register("di-tei-element-body", DocIndexerTei.class);
        register("di-tei-element-text", DocIndexerTeiText.class);
        register("di-tei-pos-function", DocIndexerTeiPosInFunctionAttr.class);

        // Register builtin formats and formats in config dirs so they can be listed
        registerFormatsFromJar(Arrays.asList("chat", "csv", "folia", "tei", "tsv-frog", "sketch-wpl", "tsv", "txt"));
        List<File> configDirs = Searcher.getConfigDirs();
        List<File> formatsDirs = new ArrayList<>();
        for (File dir: configDirs) {
            formatsDirs.add(new File(dir, "formats"));
        }
        registerFormatsInDirs(formatsDirs);
    }

    private static void registerFormatsFromJar(List<String> formatIdentifiers) {
        for (String formatIdentifier: formatIdentifiers) {
            try (InputStream is = DocumentFormats.class.getClassLoader().getResourceAsStream("formats/" + formatIdentifier + ".blf.yaml")) {
                if (is == null)
                    continue; // not found
                try (Reader reader = new BufferedReader(new InputStreamReader(is))) {
                    ConfigInputFormat format = new ConfigInputFormat(formatIdentifier, reader, false);
                    format.setReadFromFile(new File("$BLACKLAB_JAR/formats/" + formatIdentifier + ".blf.yaml"));
                    register(format);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static BufferedReader getFormatFile(String formatIdentifier) {
        ConfigInputFormat config = DocumentFormats.getConfig(formatIdentifier);
        if (config == null)
            return null;
        File f = config.getReadFromFile();
        if (f.getPath().startsWith("$BLACKLAB_JAR"))
            return new BufferedReader(new InputStreamReader(DocumentFormats.class.getClassLoader().getResourceAsStream("formats/" + formatIdentifier + ".blf.yaml")));
        return FileUtil.openForReading(f);
    }



    /**
     * Scan the supplied directories for format files and register them.
     * @param dirs directories to scan
     */
	public static void registerFormatsInDirs(List<File> dirs) {
        for (File dir: dirs) {
            if (dir.exists() && dir.canRead()) {
                FileUtil.processTree(dir, new FileTask() {
                    @Override
                    public void process(File f) {
                        if (f.getName().matches("^[\\-\\w]+\\.blf\\.(ya?ml|json)$")) {
                            // Format file found. Register it.
                            try {
                                register(new ConfigInputFormat(f));
                            } catch (InputFormatConfigException e) {
                                throw new InputFormatConfigException("Error in input format config " + f + ": " + e.getMessage());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                });
            }
        }
    }

    /**
	 * Register a DocIndexer class under an abbreviated document format identifier.
	 *
	 * @param formatAbbreviation the format abbreviation, e.g. "tei"
	 *   (NOTE: format abbreviations are case-insensitive, and are lowercased internally)
	 * @param docIndexerClass the DocIndexer class for this format
	 */
	public static void register(String formatAbbreviation, final Class<? extends DocIndexer> docIndexerClass) {
		docIndexerClasses.put(formatAbbreviation.toLowerCase(), docIndexerClass);
	}

	/**
	 * Register an input format configuration under its name.
	 *
	 * @param config input format configuration to register
	 */
	public static void register(final ConfigInputFormat config) {
        String name = config.getName().toLowerCase();
	    try {
	        config.validate();
	    } catch(IllegalArgumentException e) {
	        throw new IllegalArgumentException("Format " + name + ": " + e.getMessage());
	    }
        formats.put(name, config);
	}

	/**
	 * Get the DocIndexer class associated with the format identifier.
	 *
	 * @param formatIdentifier format identifier, e.g. "tei" or "com.example.MyIndexer"
	 * @return the DocIndexer class, or null if not found
	 */
	public static Class<? extends DocIndexer> getIndexerClass(String formatIdentifier) {
        if (!docIndexerClasses.containsKey(formatIdentifier.toLowerCase()))
            find(formatIdentifier);
		// Check if it's a known abbreviation.
		Class<?> docIndexerClass = docIndexerClasses.get(formatIdentifier.toLowerCase());
		if (docIndexerClass == null) {
			// No; is it a fully qualified class name?
			try {
				docIndexerClass = Class.forName(formatIdentifier);
			} catch (Exception e1) {
				try {
					// No. Is it a class in the BlackLab indexers package?
					docIndexerClass = Class.forName("nl.inl.blacklab.indexers." + formatIdentifier);
				} catch (Exception e) {
					// Couldn't be resolved. That's okay, we'll just return null and let
					// the application deal with it.
				}
			}
		}
		return docIndexerClass.asSubclass(DocIndexer.class);
	}

    public static DocIndexerFactory getIndexerFactory(String formatIdentifier) {
        formatIdentifier = formatIdentifier.toLowerCase();
        if (!exists(formatIdentifier))
            find(formatIdentifier);
        if (formats.containsKey(formatIdentifier))
            return new DocIndexerFactoryConfig(formats.get(formatIdentifier));
        if (docIndexerClasses.containsKey(formatIdentifier))
            return new DocIndexerFactoryClass(docIndexerClasses.get(formatIdentifier));
        return null;
	}

	public static ConfigInputFormat getConfig(String formatName) {
        if (!formats.containsKey(formatName.toLowerCase()))
            find(formatName);
        return formats.get(formatName.toLowerCase());
    }

    /**
	 * Check if a particular string denotes a valid document format.
	 *
	 * @param formatIdentifier format identifier, e.g. "tei" or "com.example.MyIndexer"
	 * @return true iff it corresponds to a format
	 */
	public static boolean exists(String formatIdentifier) {
	    formatIdentifier = formatIdentifier.toLowerCase();
		return formats.containsKey(formatIdentifier) || docIndexerClasses.containsKey(formatIdentifier);
	}

	public static abstract class FormatFinder {
	    public abstract boolean findAndRegister(String formatIdentifier);
	}

    /**
     * If the formatIdentifier matches a [fully qualified] class name that's a subclass of DocIndexer, use that.
     */
	static class FormatFinderDocIndexerClass extends FormatFinder {
        @SuppressWarnings("unchecked")
        @Override
        public boolean findAndRegister(String formatIdentifier) {
            // Is it a fully qualified class name?
            Class<? extends DocIndexer> docIndexerClass = null;
            try {
                docIndexerClass = (Class<? extends DocIndexer>) Class.forName(formatIdentifier);
            } catch (Exception e1) {
                try {
                    // No. Is it a class in the BlackLab indexers package?
                    docIndexerClass = (Class<? extends DocIndexer>) Class.forName("nl.inl.blacklab.indexers." + formatIdentifier);
                } catch (Exception e) {
                    // Couldn't be resolved. That's okay, we'll just return null and let
                    // the application deal with it.
                }
            }
            if (docIndexerClass != null) {
                register(formatIdentifier, docIndexerClass);
                return true;
            }
            return false;
        }

	}

	/**
	 * Add a way to look for formats that aren't registered yet.
	 *
	 * @param ff format finder to use
	 */
	public static void addFormatFinder(FormatFinder ff) {
	    formatFinders.add(ff);
	}

	private static boolean find(String formatIdentifier) {
	    for (int i = formatFinders.size() - 1; i >= 0; i--) {
	        FormatFinder ff = formatFinders.get(i);
	        if (ff.findAndRegister(formatIdentifier)) {
	            return true;
	        }
	    }
	    return false;
	}

	/**
	 * Description of a supported input format
	 */
	public static class FormatDesc {

	    private String formatIdentifier;

	    private String displayName;

	    private String description;

        private boolean unlisted;

		private boolean isConfigBased;

        public boolean isUnlisted() {
            return unlisted;
        }

        public String getName() {
            return formatIdentifier;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public FormatDesc(String formatIdentifier, String displayName, String description, boolean isConfigBased) {
            super();
            this.formatIdentifier = formatIdentifier;
            this.displayName = displayName;
            this.description = description;
            this.isConfigBased = isConfigBased;
        }

        public void setUnlisted(boolean b) {
            this.unlisted = b;
        }

		public boolean isConfigurationBased() {
			return isConfigBased;
		}

	}

    /**
     * Returns a sorted list of registered document format abbreviations.
     *
     * @return the list of registered abbreviations
     */
    public static List<FormatDesc> getSupportedFormats() {
        List<FormatDesc> l = new ArrayList<>();
        for (ConfigInputFormat config: formats.values()) {
            l.add(new FormatDesc(config.getName(), config.getDisplayName(), config.getDescription(), true));
        }
        for (String format: docIndexerClasses.keySet()) {
            Class<? extends DocIndexer> docIndexerClass = getIndexerClass(format);
            FormatDesc formatDesc = new FormatDesc(format, DocIndexer.getDisplayName(docIndexerClass), DocIndexer.getDescription(docIndexerClass), false);
            if (!DocIndexer.listFormat(docIndexerClass))
                formatDesc.setUnlisted(true);
            l.add(formatDesc);
        }
        return Collections.unmodifiableList(l);
    }

	/**
	 * Returns a sorted list of registered document format abbreviations.
	 *
	 * @return the list of registered abbreviations
	 * @deprecated use getSupportedFormats()
	 */
    @Deprecated
	public static List<String> list() {
		List<String> l = new ArrayList<>();
		for (ConfigInputFormat config: formats.values()) {
		    l.add(config.getName());
		}
        for (String format: docIndexerClasses.keySet()) {
            l.add(format);
        }
		return Collections.unmodifiableList(l);
	}

    public static void unregister(String formatIdentifier) {
        if (formats.containsKey(formatIdentifier)) {
            formats.remove(formatIdentifier);
        }
        if (docIndexerClasses.containsKey(formatIdentifier)) {
            docIndexerClasses.remove(formatIdentifier);
        }
    }

}
