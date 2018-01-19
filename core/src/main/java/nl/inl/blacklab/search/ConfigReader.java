package nl.inl.blacklab.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.DownloadCache;
import nl.inl.blacklab.index.ZipHandleManager;
import nl.inl.blacklab.index.config.YamlJsonReader;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;

/**
 * Reads blacklab.yaml/.json file from one of the config dirs.
 *
 * This file contains general BlackLab settings that apply to multiple
 * applications, i.e. IndexTool, QueryTool, BlackLab Server, and
 * other applications that use BlackLab.
 *
 * Config dirs are, in search order: $BLACKLAB_CONFIG_DIR/, $HOME/.blacklab/
 * or /etc/blacklab/.
 */
public class ConfigReader extends YamlJsonReader {

	private static final Logger logger = LogManager.getLogger(ConfigReader.class);

	/** Cache for getConfigDirs() */
    private static List<File> configDirs;
    /** Cache of root node of config file */
    private static JsonNode blacklabConfig;


    /**
     * Load the global blacklab configuration.
     * This file configures several global settings, as well as providing default default settings for any new {@link Searcher} constructed hereafter.
     *
     * If no explicit config file has been set by the time when the first Searcher is opened, BlackLab automatically attempts to find and load a
     * configuration file in a number of preset locations (see {@link ConfigReader#getDefaultConfigDirs()}).
     *
     * Setting a new configuration when another has already been loaded is unsupported.
     *
     * @param file
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void setConfigFile(File file) throws FileNotFoundException, IOException {

    	if (file == null || !file.canRead())
    		throw new FileNotFoundException("Configuration file " + file + " is unreadable.");

    	if (!FilenameUtils.isExtension(file.getName(), Arrays.asList("yaml", "yml", "json")))
    		throw new IllegalArgumentException("Configuration file " + file + " is of an unsupported type.");

    	if (blacklabConfig != null)
    		throw new UnsupportedOperationException("Cannot load configuration file "+file+" - another configuration file has already been loaded.");

		boolean isJson = file.getName().endsWith(".json");
		try (BufferedReader reader = FileUtil.openForReading(file)) {
			setConfigFile(reader, isJson);
		}
    }

    /**
     * See {@link ConfigReader#setConfigFile(File)}.
     *
     * The reader must be closed by the user.
     *
     * @param reader
     * @param isJson
     * @throws JsonProcessingException
     * @throws IOException
     */
    public static void setConfigFile(Reader reader, boolean isJson) throws JsonProcessingException, IOException{
    	if (blacklabConfig != null)
    		throw new UnsupportedOperationException("Cannot load configuration file - another configuration file has already been loaded.");

    	ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();

    	JsonNode parsedConfig = mapper.readTree(reader);

	    readGlobalSettings(parsedConfig);

	    blacklabConfig = parsedConfig;
    }


    private static void loadDefaultConfig() {
    	if (blacklabConfig != null)
    		return;

		try {
			File file = FileUtil.findFile(getDefaultConfigDirs(), "blacklab", Arrays.asList("yaml", "yml", "json"));
			if (file != null)
				setConfigFile(file);
		} catch (IOException e) {
			logger.warn("Could not load default configuration file: " + e.getMessage());
		}
    }

    /**
     * Get the global blacklab configuration file.
     * Null will be returned if no config has been set and no default configuration has been loaded yet.
     * Will not load the default configuration.
     *
     * @return the loaded configuration file, or null if no config has been loaded yet.
     */
    public static JsonNode getConfigFile() {
    	return blacklabConfig;
    }

    /**
     * Configure the searcher according to the blacklab configuration file.
     *
     * @param searcher
     * @throws IOException
     */
    public static void applyConfig(Searcher searcher) throws IOException {
    	if (blacklabConfig == null)
    		loadDefaultConfig();

    	if (blacklabConfig != null)
    		readSearcherSettings(blacklabConfig, searcher);
    }

    private static void readSearcherSettings(JsonNode root, Searcher searcher) {
        obj(root, "root node");
        Iterator<Entry<String, JsonNode>> it = root.fields();

        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "search": readSearch(obj(e), searcher); break;
            case "indexing":
            case "debug":
            	break;
            default:
                throw new IllegalArgumentException("Unknown top-level key " + e.getKey());
            }
        }
    }

    private static void readSearch(ObjectNode obj, Searcher searcher) {
        HitsSettings hitsSett = searcher.hitsSettings();
        Iterator<Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "collator": readCollator(e, searcher); break;
            case "contextSize": hitsSett.setContextSize(integer(e)); break;
            case "maxHitsToRetrieve": hitsSett.setMaxHitsToRetrieve(integer(e)); break;
            case "maxHitsToCount": hitsSett.setMaxHitsToCount(integer(e)); break;
            default:
                throw new IllegalArgumentException("Unknown key " + e.getKey() + " in search section");
            }
        }
    }

    private static void readCollator(Entry<String, JsonNode> e, Searcher searcher) {
        Collator collator;
        if (e.getValue() instanceof ObjectNode) {
            Iterator<Entry<String, JsonNode>> it = obj(e).fields();
            String language = null, country = null, variant = null;
            while (it.hasNext()) {
                Entry<String, JsonNode> e2 = it.next();
                switch (e2.getKey()) {
                case "language": language = str(e2); break;
                case "country": country = str(e2); break;
                case "variant": variant = str(e2); break;
                default:
                    throw new IllegalArgumentException("Unknown key " + e.getKey() + " in collator (must have language, can have country and variant)");
                }
            }
            if (language == null || country == null && variant != null)
                throw new IllegalArgumentException("Collator must have language, language+country or language+country+variant");
            if (country == null)
                collator = Collator.getInstance(new Locale(language));
            else if (variant == null)
                collator = Collator.getInstance(new Locale(language, country));
            else
                collator = Collator.getInstance(new Locale(language, country, variant));
        } else {
            collator = Collator.getInstance(new Locale(str(e)));
        }
        searcher.setCollator(collator);
    }

    private static void readGlobalSettings(JsonNode root) {
    	obj(root, "root node");
        Iterator<Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "indexing": readIndexing(obj(e)); break;
            case "debug": readDebug(obj(e)); break;
            case "search":
            	break;
            default:
                throw new IllegalArgumentException("Unknown top-level key " + e.getKey());
            }
        }
    }

    private static void readDebug(ObjectNode obj) {
        Iterator<Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "trace": readTrace(obj(e)); break;
            default:
                throw new IllegalArgumentException("Unknown key " + e.getKey() + " in debug section");
            }
        }
    }

    private static void readTrace(ObjectNode obj) {
        Iterator<Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "indexOpening": Searcher.setTraceIndexOpening(bool(e)); break;
            case "optimization": Searcher.setTraceOptimization(bool(e)); break;
            case "queryExecution": Searcher.setTraceQueryExecution(bool(e)); break;
            default:
                throw new IllegalArgumentException("Unknown key " + e.getKey() + " in trace section");
            }
        }
    }

    private static void readIndexing(ObjectNode indexing) {
        Iterator<Entry<String, JsonNode>> it = indexing.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "downloadAllowed": DownloadCache.setDownloadAllowed(bool(e)); break;
            case "downloadCacheMaxFileSizeMegs": DownloadCache.setMaxFileSizeMegs(integer(e)); break;
            case "downloadCacheDir": DownloadCache.setDir(new File(str(e))); break;
            case "downloadCacheSizeMegs": DownloadCache.setSizeMegs(integer(e)); break;
            case "zipFilesMaxOpen": ZipHandleManager.setMaxOpen(integer(e)); break;
            default:
                throw new IllegalArgumentException("Unknown key " + e.getKey() + " in indexing section");
            }
        }
    }

	/**
	 * Return a list of directories that should be searched for BlackLab-related configuration files.
	 *
	 * May be used by applications to locate BlackLab-related configuration, such as
	 * input format definition files or other configuration files. IndexTool and BlackLab Server use
	 * this.
	 *
	 * The directories returned are (in decreasing priority):
	 * - $BLACKLAB_CONFIG_DIR (if env. var. is defined)
	 * - $HOME/.blacklab
	 * - /etc/blacklab
	 * - /vol1/etc/blacklab (legacy, will be removed)
	 * - /tmp (legacy, will be removed)
	 *
	 * A convenient method to use with this is {@link FileUtil#findFile(List, String, List)}.
	 *
	 * @return list of directories to search in decreasing order of priority
	 */
    public static List<File> getDefaultConfigDirs() {
        if (configDirs == null) {
            configDirs = new ArrayList<>();
            String strConfigDir = System.getenv("BLACKLAB_CONFIG_DIR");
            if (strConfigDir != null && strConfigDir.length() > 0) {
                File configDir = new File(strConfigDir);
                if (configDir.exists()) {
                    if (!configDir.canRead())
                        logger.warn("BLACKLAB_CONFIG_DIR points to a unreadable directory: " + strConfigDir);
                    configDirs.add(configDir);
                } else {
                    logger.warn("BLACKLAB_CONFIG_DIR points to a non-existent directory: " + strConfigDir);
                }
            }
            configDirs.add(new File(System.getProperty("user.home"), ".blacklab"));
            configDirs.add(new File("/etc/blacklab"));
            configDirs.add(new File("/vol1/etc/blacklab")); // TODO: remove, INT-specific
            configDirs.add(new File(System.getProperty("java.io.tmpdir")));
        }
        return new ArrayList<>(configDirs);
    }
}
