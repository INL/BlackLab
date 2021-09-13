package nl.inl.blacklab.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.util.Json;

public class BlackLabConfig {

    private static final Logger logger = LogManager.getLogger(BlackLabConfig.class);

    private static BlackLabConfig read(Reader reader, boolean isJson) throws InvalidConfiguration {
        try {
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            return mapper.readValue(reader, BlackLabConfig.class);
        } catch (IOException e) {
            throw new InvalidConfiguration("Invalid configuration (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Load the global blacklab configuration. This file configures several global
     * settings, as well as providing default settings for any new {@link BlackLabIndexImpl}
     * constructed hereafter.
     *
     * If no explicit config file has been set by the time when the first BlackLabIndex
     * is opened, BlackLab automatically attempts to find and load a configuration
     * file in a number of preset locations.
     *
     * Attempting to set another configuration when one is already loaded will throw
     * an UnsupportedOperationException.
     *
     * @param file
     * @return configuration
     * @throws FileNotFoundException
     * @throws IOException
     */
    public synchronized static BlackLabConfig readConfigFile(File file) throws FileNotFoundException, IOException {
        if (file == null || !file.canRead())
            throw new FileNotFoundException("Configuration file " + file + " is unreadable.");

        if (!FilenameUtils.isExtension(file.getName(), Arrays.asList("yaml", "yml", "json")))
            throw new InvalidConfiguration("Configuration file " + file + " is of an unsupported type.");

        boolean isJson = file.getName().endsWith(".json");
        return readConfigFile(file.getCanonicalPath(), FileUtils.readFileToString(file, StandardCharsets.UTF_8), isJson);
    }

    /**
     * See {@link OldConfigReader#readConfigFile(File)}.
     *
     * The reader must be closed by the user.
     *
     * @param reader
     * @param isJson
     * @throws JsonProcessingException
     * @throws IOException
     */
    private synchronized static BlackLabConfig readConfigFile(String fileName, String fileContents, boolean isJson) throws InvalidConfiguration {
        ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();

        logger.debug("Reading global BlackLab config");
        JsonNode parsedConfig;
        try {
            parsedConfig = mapper.readTree(new StringReader(fileContents));
        } catch (IOException e) {
            throw new InvalidConfiguration("Error reading BlackLab config file (" + fileName + "): " + e.getMessage(), e);
        }

        // Is this the new config format, or the old one?
        if (parsedConfig.get("configVersion") != null) {
            // New config format
            return BlackLabConfig.read(new StringReader(fileContents), isJson);
        } else {
            // Old config format
            logger.error("You are using the old configuration file format. This is no longer supported.");
            logger.error("Please upgrade to the new format. See https://inl.github.io/BlackLab/configuration-files.html");
            throw new InvalidConfiguration("Your configuration files are in the old, no longer supported format. Please upgrade: https://inl.github.io/BlackLab/configuration-files.html");
        }
    }

    private int configVersion = 2;

    private BLConfigSearch search = new BLConfigSearch();

    private BLConfigIndexing indexing = new BLConfigIndexing();

    private BLConfigPlugins plugins = new BLConfigPlugins();

    BLConfigLog log = new BLConfigLog();

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }

    public BLConfigSearch getSearch() {
        return search;
    }

    public void setSearch(BLConfigSearch search) {
        this.search = search;
    }

    public BLConfigIndexing getIndexing() {
        return indexing;
    }

    public void setIndexing(BLConfigIndexing indexing) {
        this.indexing = indexing;
    }

    public BLConfigPlugins getPlugins() {
        return plugins;
    }

    public void setPlugins(BLConfigPlugins plugins) {
        this.plugins = plugins;
    }

    public BLConfigLog getLog() {
        return log;
    }

    public void setLog(BLConfigLog log) {
        this.log = log;
    }
}