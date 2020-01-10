package nl.inl.blacklab.config;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.indexers.config.YamlJsonReader;

/**
 * Reads blacklab.yaml/.json file from one of the config dirs.
 *
 * This file contains general BlackLab settings that apply to multiple
 * applications, i.e. IndexTool, QueryTool, BlackLab Server, and other
 * applications that use BlackLab.
 *
 * Config dirs are, in search order: $BLACKLAB_CONFIG_DIR/, $HOME/.blacklab/ or
 * /etc/blacklab/.
 */
public class OldConfigReader extends YamlJsonReader {

    private static BLConfigSearch readSearcherSettings(JsonNode obj) {
        BLConfigSearch config = new BLConfigSearch();
        Iterator<Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "collator":
                config.setCollator(readCollator(e));
                break;
            case "contextSize":
                config.setContextSize(integer(e));
                break;
            case "maxHitsToRetrieve":
                config.setMaxHitsToRetrieve(integer(e));
                break;
            case "maxHitsToCount":
                config.setMaxHitsToCount(integer(e));
                break;
            case "fiMatchFactor":
                config.setFiMatchFactor(integer(e));
                break;
            default:
                throw new InvalidConfiguration("Unknown key " + e.getKey() + " in search section");
            }
        }
        return config;
    }

    private static BLConfigCollator readCollator(Entry<String, JsonNode> e) {
        BLConfigCollator config = new BLConfigCollator();
        if (e.getValue() instanceof ObjectNode) {
            Iterator<Entry<String, JsonNode>> it = obj(e).fields();
            while (it.hasNext()) {
                Entry<String, JsonNode> e2 = it.next();
                switch (e2.getKey()) {
                case "language":
                    config.setLanguage(str(e2));
                    break;
                case "country":
                    config.setCountry(str(e2));
                    break;
                case "variant":
                    config.setVariant(str(e2));
                    break;
                default:
                    throw new InvalidConfiguration("Unknown key " + e.getKey()
                            + " in collator (must have language, can have country and variant)");
                }
            }
            if (config.getLanguage() == null || config.getCountry() == null && config.getVariant() != null)
                throw new InvalidConfiguration(
                        "Collator must have language, language+country or language+country+variant");
        } else {
            config.setCountry(str(e));
        }
        return config;
    }

    static BlackLabConfig readGlobalSettings(JsonNode root) {
        BlackLabConfig config = new BlackLabConfig();
        obj(root, "root node");
        Iterator<Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "indexing":
                config.setIndexing(readIndexing(obj(e)));
                break;
            case "plugins":
                config.setPlugins(readPlugins(obj(e)));
                break;
            case "debug":
                config.setLog(readDebug(obj(e)));
                break;
            case "search":
                config.setSearch(readSearcherSettings(obj(e)));
                break;
            default:
                throw new InvalidConfiguration("Unknown top-level key " + e.getKey());
            }
        }
        return config;
    }

    private static BLConfigPlugins readPlugins(ObjectNode pluginConfig) {
        obj(pluginConfig, "plugin configs");
        BLConfigPlugins config = new BLConfigPlugins();
        Iterator<Entry<String, JsonNode>> it = pluginConfig.fields();
        Map<String, Map<String, String>> plugins = new HashMap<>();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "delayInitialization":
                JsonNode delayNode = e.getValue();
                boolean delayInitialization = (delayNode != null && !(delayNode instanceof NullNode) && delayNode.asBoolean());
                config.setDelayInitialization(delayInitialization);
                break;
            default:
                plugins.put(e.getKey(), readPlugin(e.getValue()));
                break;
            }
        }
        config.setPlugins(plugins);
        return config;
    }

    private static Map<String, String> readPlugin(JsonNode jsonParams) {
        Map<String, String> params = new HashMap<>();
        obj(jsonParams, "plugin config");
        Iterator<Entry<String, JsonNode>> it = jsonParams.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            params.put(e.getKey(), e.getValue().asText());
        }
        return params;
    }

    private static BLConfigLog readDebug(ObjectNode obj) {
        BLConfigLog config = new BLConfigLog();
        Iterator<Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "trace":
                config.setTrace(readTrace(obj(e)));
                break;
            default:
                throw new InvalidConfiguration("Unknown key " + e.getKey() + " in debug section");
            }
        }
        return config;
    }

    private static BLConfigTrace readTrace(ObjectNode obj) {
        BLConfigTrace config = new BLConfigTrace();
        Iterator<Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "indexOpening":
                config.setIndexOpening(bool(e));
                break;
            case "optimization":
                config.setOptimization(bool(e));
                break;
            case "queryExecution":
                config.setQueryExecution(bool(e));
                break;
            default:
                throw new InvalidConfiguration("Unknown key " + e.getKey() + " in trace section");
            }
        }
        return config;
    }

    public static BLConfigIndexing readIndexing(ObjectNode indexing) {
        BLConfigIndexing config = new BLConfigIndexing();
        Iterator<Entry<String, JsonNode>> it = indexing.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "downloadAllowed":
                config.setDownloadAllowed(bool(e));
                break;
            case "downloadCacheMaxFileSizeMegs":
                config.setDownloadCacheMaxFileSizeMegs(integer(e));
                break;
            case "downloadCacheDir":
                config.setDownloadCacheDir(str(e));
                break;
            case "downloadCacheSizeMegs":
                config.setDownloadCacheSizeMegs(integer(e));
                break;
            case "zipFilesMaxOpen":
                config.setZipFilesMaxOpen(integer(e));
                break;
            case "maxMetadataValuesToStore":
                config.setMaxMetadataValuesToStore(integer(e));
                break;
            default:
                throw new InvalidConfiguration("Unknown key " + e.getKey() + " in indexing section");
            }
        }
        return config;
    }
}
