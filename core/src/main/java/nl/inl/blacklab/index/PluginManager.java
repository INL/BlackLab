package nl.inl.blacklab.index;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.index.config.YamlJsonReader;
import nl.inl.blacklab.indexers.preprocess.ConvertPlugin;
import nl.inl.blacklab.indexers.preprocess.Plugin;
import nl.inl.blacklab.indexers.preprocess.Plugin.PluginException;
import nl.inl.blacklab.indexers.preprocess.TagPlugin;

/**
 * Responsible for loading file conversion and tagging plugins.
 * <p>
 * It will attempt to load all conversion and tagging plugins (according to the {@link ServiceLoader} system) and
 * initialize them with their respective settings from the main blacklab config.
 * <p>
 * A jar that wishes to register a plugin must contain a file named "nl.inl.blacklab.indexers.preprocess.(Tag|Covert)Plugin" inside
 * "META-INF/services/", containing the qualified classNames of the implementations they contain.
 */
public class PluginManager {
    private static final Logger logger = LogManager.getLogger(PluginManager.class);

    /** a pluginId may only contain a-z, A-Z, 0-9, and _ */
    private static final Pattern PLUGINID_PATTERN = Pattern.compile("[\\w]+");

    private static boolean isInitialized = false;

    private static Map<String, ConvertPlugin> convertPlugins = new HashMap<>();
    private static Map<String, TagPlugin> tagPlugins = new HashMap<>();


    /*
     * Nothing to do; initialization happens when the blacklab config is loaded.
     * The blacklab Config is automatically loaded when the first Searcher is opened, or earlier by a user library.
     * So plugin formats should always be visible by the time they're needed.
     * (except when trying to query available formats before opening a searcher or loading a config...this is an edge case)
     */
    private PluginManager() {}

    /**
     * Attempts to load and initialize all plugin classes on the classpath, passing the values in the config to the matching plugin.
     *
     * @param pluginConfig the plugin configurations collection object. The format for this object is
     *
     *        <pre>
     * {
     *   "pluginId": {
     *     // arbitrary plugin config here
     *   },
     *
     *   "anotherPluginId": { ... },
     *   ...
     * }
     *        </pre>
     */
    public static void initPlugins(ObjectNode pluginConfig) {
        if (isInitialized)
            throw new IllegalStateException("PluginManager already initialized");
        isInitialized = true;

        logger.info("Initializing plugin system");

        convertPlugins = initPlugins(ConvertPlugin.class, pluginConfig);
        tagPlugins = initPlugins(TagPlugin.class, pluginConfig);

        logger.info("Finished Initializing plugin system");

    }

    private static <T extends Plugin> Map<String, T> initPlugins(Class<T> pluginClass, ObjectNode pluginConfig) {
        Map<String, T> plugins = new HashMap<>();

        Iterator<T> it = ServiceLoader.load(pluginClass).iterator();
        while (it.hasNext()) {
            String id = null;

            try {
                T plugin = it.next();
                id = plugin.getId();
                if (!PLUGINID_PATTERN.matcher(id).matches()) {
                    logger.warn("Plugin id " + id + " (class " + plugin.getClass().getCanonicalName() +
                        ") is not a valid id; ignoring plugin.");
                    continue;
                }

                JsonNode config = pluginConfig.get(plugin.getId());

                logger.debug("Initializing plugin " + plugin.getDisplayName());
                if (config == null || config instanceof NullNode)
                    plugin.init(null);
                else
                    plugin.init(YamlJsonReader.obj(config, plugin.getId()));

                plugins.put(id, plugin);
                logger.debug("Initialized plugin " + plugin.getDisplayName());
            } catch (ServiceConfigurationError e) {
                logger.error("Plugin failed to load: " + e.getMessage(), e);
            } catch (PluginException e) {
                logger.error("Plugin " + id + " failed to initialize: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.error("Plugin " + (id == null ? "(unknown)" : id) + " failed to load: " + e.getMessage(), e);
            }
        }

        return plugins;
    }

    public static Optional<ConvertPlugin> getConverter(String convertPluginId) {
        if (!isInitialized)
            throw new UnsupportedOperationException("Plugin system is not initialized, place a top-level key \"plugins\" with per-plugin configuration in your blacklab config to use plugins.");
        return Optional.ofNullable(convertPlugins.get(convertPluginId));
    }

    public static Optional<TagPlugin> getTagger(String tagPluginId) {
        if (!isInitialized)
            throw new UnsupportedOperationException("Plugin system is not initialized, place a top-level key \"plugins\" with per-plugin configuration in your blacklab config to use plugins.");
        return Optional.ofNullable(tagPlugins.get(tagPluginId));
    }
}
