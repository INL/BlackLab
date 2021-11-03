package nl.inl.blacklab.index;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.preprocess.ConvertPlugin;
import nl.inl.blacklab.indexers.preprocess.TagPlugin;

/**
 * Responsible for loading file conversion and tagging plugins.
 * <p>
 * It will attempt to load all conversion and tagging plugins (according to the
 * {@link ServiceLoader} system) and initialize them with their respective
 * settings from the main blacklab config.
 * <p>
 * A jar that wishes to register a plugin must contain a file named
 * "nl.inl.blacklab.indexers.preprocess.(Tag|Covert)Plugin" inside
 * "META-INF/services/", containing the qualified classNames of the
 * implementations they contain.
 */
public class PluginManager {
    private static class PluginData<T extends Plugin> {
        public boolean initialized;
        public PluginException initializationException;

        public final T plugin;
        private final Map<String, String> configMap;

        public PluginData(T plugin, Map<String, String> configMap) {
            this.configMap = configMap;
            this.plugin = plugin;
        }
    }

    private static final Logger logger = LogManager.getLogger(PluginManager.class);

    /** a pluginId may only contain a-z, A-Z, 0-9, and _ */
    private static final Pattern PLUGINID_PATTERN = Pattern.compile("[\\w]+");

    /**
     * Delay initialization of plugins until they are first used. Useful for
     * development
     */
    private static final String PROP_DELAY_INITIALIZATION = "delayInitialization";

    /** Is the plugin system itself initialized */
    private static boolean isInitialized = false;

    private static Map<String, PluginData<ConvertPlugin>> convertPlugins = new HashMap<>();
    private static Map<String, PluginData<TagPlugin>> tagPlugins = new HashMap<>();

    // Nothing to do; initialization happens when the blacklab config is loaded.
    // The blacklab Config is automatically loaded when the first BlackLabIndex is
    // opened, or earlier by a user library.
    // So plugin formats should always be visible by the time they're needed.
    // (except when trying to query available formats before opening an index or
    // loading a config...this is an edge case)
    private PluginManager() {
    }

    /**
     * Attempts to load and initialize all plugin classes on the classpath, passing
     * the values in the config to the matching plugin.
     *
     * @param pluginConfig the plugin configurations collection object. The format
     *            for this object is
     *
     *            <pre>
     * {
     *   "pluginId": {
     *     // arbitrary plugin config here
     *   },
     *
     *   "anotherPluginId": { ... },
     *   ...
     * }
     *            </pre>
     */
    public static void initPlugins(BLConfigPlugins pluginConfig) {
        if (isInitialized)
            throw new IllegalStateException("PluginManager already initialized");
        isInitialized = true;

        logger.debug("Initializing plugin system...");
        
        boolean delayInitialization = pluginConfig.isDelayInitialization();

        // First load all plugins, so we have the full list of plugins available.
        convertPlugins = loadPlugins(ConvertPlugin.class, pluginConfig);
        tagPlugins = loadPlugins(TagPlugin.class, pluginConfig);

        // Some plugins take a LONG time to init, if we block, we block the loading of the config
        // Which in turn blocks the whole of blacklab(-server), so don't do that
        if (!delayInitialization) {
            CompletableFuture.runAsync(() -> {
                // only now they're all located, initialize them
                logger.trace("Config setting " + PROP_DELAY_INITIALIZATION + " is false, initializing plugins...");
                initializePlugins(convertPlugins);
                initializePlugins(tagPlugins);
                logger.trace("Finished Initializing plugin system");
            });
        } else {
            logger.trace("Config setting " + PROP_DELAY_INITIALIZATION
                    + " is true, plugins will be initialized on first use.");
            logger.trace("Finished Initializing plugin system");
        }
    }

    private static <T extends Plugin> Map<String, PluginData<T>> loadPlugins(Class<T> pluginClass,
            BLConfigPlugins pluginConfig) {
        Map<String, PluginData<T>> plugins = new HashMap<>();

        Iterator<T> it = ServiceLoader.load(pluginClass).iterator();
        Map<String, Map<String, String>> pluginParamConfig = pluginConfig.getPlugins();
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

                // Config available, or plugin needs no config?
                if (!plugin.needsConfig() || pluginParamConfig.containsKey(id)) {
                    // Yes, add the plugin data to our map.
                    PluginData<T> data = new PluginData<>(plugin, pluginParamConfig.get(id));
                    plugins.put(id, data);
                }
            } catch (ServiceConfigurationError e) {
                logger.error("Plugin failed to load: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.error("Plugin " + (id == null ? "(unknown)" : id) + " failed to load: " + e.getMessage(), e);
            }
        }

        return plugins;
    }

    public static Optional<ConvertPlugin> getConverter(String convertPluginId) throws PluginException {
        if (!isInitialized)
            throw new UnsupportedOperationException(
                    "Plugin system is not initialized, place a top-level key \"plugins\" with " +
                            "per-plugin configuration in your blacklab config to use plugins.");

        Optional<PluginData<ConvertPlugin>> data = Optional.ofNullable(convertPlugins.get(convertPluginId));
        if (data.isPresent())
            initializePlugin(data.get());

        return data.map(d -> d.plugin);
    }

    public static Optional<TagPlugin> getTagger(String tagPluginId) throws PluginException {
        if (!isInitialized)
            throw new UnsupportedOperationException(
                    "Plugin system is not initialized, place a top-level key \"plugins\" with " +
                            "per-plugin configuration in your blacklab config to use plugins.");

        Optional<PluginData<TagPlugin>> data = Optional.ofNullable(tagPlugins.get(tagPluginId));
        if (data.isPresent())
            initializePlugin(data.get());

        return data.map(d -> d.plugin);
    }

    /**
     * Initialize the plugin, if it exists and is currently uninitialized.
     * Previously encountered errors are rethrown. If am error is encountered, it is
     * stored in the plugin data and rethrown.
     *
     * @param data plugin data
     * @throws PluginException when the plugin fails to initialize, care should be
     *             taken by the caller to remove it from the list of plugins when
     *             this occurs.
     */
    private static void initializePlugin(PluginData<?> data) throws PluginException {
        synchronized (data.plugin) {
            if (data.initializationException != null)
                throw data.initializationException;
            if (data.initialized)
                return;

            try {
                logger.debug("Initializing plugin " + data.plugin.getDisplayName());
                data.plugin.init(data.configMap);
                logger.debug("Initialized plugin " + data.plugin.getDisplayName());
            } catch (PluginException e) {
                data.initializationException = e;
                throw e;
            } catch (Exception e) {
                data.initializationException = new PluginException("Error during initialization.", e);
                throw data.initializationException;
            } finally {
                data.initialized = true;
            }
        }
    }

    /**
     * Used to initialize all plugins in one go when
     * {@link #PROP_DELAY_INITIALIZATION} is false.
     *
     * @param plugins
     */
    private static <T extends Plugin> void initializePlugins(Map<String, PluginData<T>> plugins) {
        plugins.values().forEach(pd -> {
            try {
                initializePlugin(pd);
            } catch (PluginException e) {
                // exception already cached in plugindata, no need to throw.
                logger.error("Plugin " + pd.plugin.getId() + " failed to initialize: " + e.getMessage(), e);
            }
        });
    }
}
