package nl.inl.blacklab.indexers.preprocess;

import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface of converting a plugin (including using external services) Only a
 * single instance of a plugin is constructed, so plugins must be threadsafe.
 *
 * A plugin must define a no-argument constructor.
 */
public interface Plugin {

    class PluginException extends Exception {
        public PluginException() {
            super();
        }

        public PluginException(String message, Throwable cause) {
            super(message, cause);
        }

        public PluginException(String message) {
            super(message);
        }

        public PluginException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Return a globally unique id for this plugin class. This ID must be constant
     * across runs and versions.
     *
     * @return the global identifier for this plugin
     */
    String getId();

    /**
     * Return a user-friendly name for this plugin that can be used in messages,
     * etc.
     *
     * @return a user-friendly name for this plugin
     */
    String getDisplayName();

    String getDescription();

    /**
     * Initializes the plugin, called once after the initial loading of the class.
     *
     * @param config the config settings for this plugin
     * @throws PluginException
     */
    void init(Optional<ObjectNode> config) throws PluginException;
}
