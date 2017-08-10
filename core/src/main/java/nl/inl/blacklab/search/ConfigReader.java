package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.text.Collator;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map.Entry;

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

    public static void read(Reader r, boolean isJson, Searcher searcher) throws IOException {
        ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
        JsonNode root = mapper.readTree(r);
        read(root, searcher);
    }

    public static void read(File file, Searcher searcher) throws IOException {
        read(FileUtil.openForReading(file), file.getName().endsWith(".json"), searcher);
    }

    protected static void read(JsonNode root, Searcher searcher) {
        obj(root, "root node");
        Iterator<Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> e = it.next();
            switch (e.getKey()) {
            case "indexing": readIndexing(obj(e)); break;
            case "debug": readDebug(obj(e)); break;
            case "search": readSearch(obj(e), searcher); break;
            default:
                throw new IllegalArgumentException("Unknown top-level key " + e.getKey());
            }
        }
    }

    public static void readSearch(ObjectNode obj, Searcher searcher) {
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
                throw new IllegalArgumentException("Unknown key " + e.getKey() + " in debug section");
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

    public static void readIndexing(ObjectNode indexing) {
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

}
