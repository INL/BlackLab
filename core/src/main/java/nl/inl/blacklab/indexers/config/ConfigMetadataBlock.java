package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configuration for a block of metadata fields. */
public class ConfigMetadataBlock {

    /** Where the block can be found */
    private String containerPath = ".";

    /** What default analyzer to use for these fields */
    private String defaultAnalyzer = "";

    /** Metadata fields */
    private List<ConfigMetadataField> fields = new ArrayList<>();

    /** Metadata fields (except forEach's) by name */
    private Map<String, ConfigMetadataField> fieldsByName = new LinkedHashMap<>();

    public void validate() {
        for (ConfigMetadataField f : fields) {
            f.validate();
        }
    }

    public ConfigMetadataBlock copy() {
        ConfigMetadataBlock result = new ConfigMetadataBlock();
        result.setContainerPath(containerPath);
        result.setDefaultAnalyzer(defaultAnalyzer);
        for (ConfigMetadataField f : fields) {
            result.addMetadataField(f.copy());
        }
        return result;
    }

    public String getContainerPath() {
        return containerPath;
    }

    public void setContainerPath(String containerPath) {
        this.containerPath = containerPath;
    }

    public List<ConfigMetadataField> getFields() {
        return fields;
    }

    public ConfigMetadataField getField(String name) {
        return fieldsByName.get(name);
    }
    
    public ConfigMetadataField getOrCreateField(String name) {
        ConfigMetadataField f = getField(name);
        if (f == null) {
            f = new ConfigMetadataField();
            f.setName(name);
            addMetadataField(f);
        }
        return f;
    }

    public void addMetadataField(ConfigMetadataField f) {
        // If no custom analyzer specified, inherit from block
        if (f.getAnalyzer().equals(""))
            f.setAnalyzer(defaultAnalyzer);
        fields.add(f);
        if (!f.isForEach())
            fieldsByName.put(f.getName(), f);
    }

    public String getAnalyzer() {
        return defaultAnalyzer;
    }

    public void setDefaultAnalyzer(String defaultAnalyzer) {
        this.defaultAnalyzer = defaultAnalyzer;
    }

    public ConfigMetadataField getMetadataField(String fieldName) {
        return fieldsByName.get(fieldName);
    }

    @Override
    public String toString() {
        return "ConfigMetadataBlock [containerPath=" + containerPath + "]";
    }

}
