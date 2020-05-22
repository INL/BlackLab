package nl.inl.blacklab.server.config;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import nl.inl.blacklab.config.BLConfigIndexing;
import nl.inl.blacklab.config.BLConfigLog;
import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.config.BLConfigSearch;
import nl.inl.blacklab.config.BlackLabConfig;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.util.Json;

public class BLSConfig {
    
    public static BLSConfig read(File configFile) throws InvalidConfiguration {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(configFile, BLSConfig.class);
        } catch (IOException e) {
            throw new InvalidConfiguration("Invalid configuration file: " + configFile + " (" + e.getMessage() + ")", e);
        }
    }

    public static BLSConfig read(Reader reader, boolean isJson) throws InvalidConfiguration {
        try {
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            return mapper.readValue(reader, BLSConfig.class);
        } catch (IOException e) {
            throw new InvalidConfiguration("Invalid configuration (" + e.getMessage() + ")", e);
        }
    }

    private int configVersion = 2;

    private List<String> indexLocations = Collections.emptyList();
    
    private String userIndexes = null;
    
    private BLSConfigProtocol protocol = new BLSConfigProtocol();
    
    private BLSConfigParameters parameters = new BLSConfigParameters();
    
    private BLSConfigCache cache = new BLSConfigCache();
    
    private BLSConfigPerformance performance = new BLSConfigPerformance();
    
    private BLSConfigDebug debug = new BLSConfigDebug();
    
    private BLSConfigAuth authentication = new BLSConfigAuth();
    
    // BlackLab-global configuration

    private BLConfigLog log = new BLConfigLog();
    
    private BLConfigSearch search = new BLConfigSearch();
    
    private BLConfigIndexing indexing = new BLConfigIndexing();
    
    private BLConfigPlugins plugins = new BLConfigPlugins();

    /** The BlackLab parts of the config together in a bundle, for easy retrieval. */
    private BlackLabConfig blackLabConfig;
    
    public List<String> getIndexLocations() {
        return indexLocations;
    }

    public void setIndexLocations(List<String> indexes) {
        this.indexLocations = indexes;
    }

    public String getUserIndexes() {
        return userIndexes;
    }

    public void setUserIndexes(String userIndexes) {
        this.userIndexes = userIndexes;
    }

    public BLSConfigProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(BLSConfigProtocol protocol) {
        this.protocol = protocol;
    }

    public BLSConfigParameters getParameters() {
        return parameters;
    }

    public void setParameters(BLSConfigParameters parameters) {
        this.parameters = parameters;
    }

    public BLSConfigCache getCache() {
        return cache;
    }

    public void setCache(BLSConfigCache cache) {
        this.cache = cache;
    }

    public BLSConfigPerformance getPerformance() {
        return performance;
    }

    public void setPerformance(BLSConfigPerformance performance) {
        this.performance = performance;
    }

    public BLConfigLog getLog() {
        return log;
    }

    public void setLog(BLConfigLog log) {
        this.log = log;
    }

    public BLSConfigDebug getDebug() {
        return debug;
    }

    public void setDebug(BLSConfigDebug debug) {
        this.debug = debug;
    }

    public BLSConfigAuth getAuthentication() {
        return authentication;
    }

    public void setAuthentication(BLSConfigAuth authentication) {
        this.authentication = authentication;
    }

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
    
    public BlackLabConfig getBLConfig() {
        if (blackLabConfig == null) {
            blackLabConfig = new BlackLabConfig();
            blackLabConfig.setConfigVersion(configVersion);
            blackLabConfig.setIndexing(indexing);
            blackLabConfig.setLog(log);
            blackLabConfig.setPlugins(plugins);
            blackLabConfig.setSearch(search);
        }
        return blackLabConfig;
    }

    public void setBLConfig(BlackLabConfig blackLabConfig) {
        this.blackLabConfig = blackLabConfig;
    }
}