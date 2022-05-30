package org.ivdnt.blacklab.aggregator;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class AggregatorConfig {

    private static AggregatorConfig instance;

    public static AggregatorConfig get() {
        if (instance == null)
            instance = readConfig();
        return instance;
    }

    /** BlackLab Server instances to aggregate */
    private List<String> nodes;

    static AggregatorConfig readConfig() {
        File configFile = locateConfigFile();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(configFile, AggregatorConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Error reading aggregator config " + configFile, e);
        }
    }

    private static File locateConfigFile() {
        String path = System.getenv("BLACKLAB_AGGREGATOR_CONFIG_FILE");
        File configFile;
        if (StringUtils.isEmpty(path)) {
            configFile = new File(System.getProperty("user.home") + "/.blacklab/aggregator.yaml");
            if (!configFile.exists())
                configFile = new File("/etc/blacklab/aggregator.yaml");
            if (!configFile.exists())
                configFile = new File("/vol1/etc/blacklab/aggregator.yaml");
            if (!configFile.exists())
                throw new RuntimeException("No config file found in $BLACKLAB_AGGREGATOR_CONFIG_FILE, ~/.blacklab/aggregator.yaml, or /etc/blacklab/aggregator.yaml");
        } else
            configFile = new File(path);
        return configFile;
    }

    private AggregatorConfig() {}

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public String getFirstNodeUrl() {
        return nodes.get(0);
    }
}
