package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.List;

/** Configuration for a block of metadata fields. */
public class ConfigMetadataBlock {

    /** Where the block can be found */
    private String containerPath = ".";

    /** What default analyzer to use for these fields */
    private String analyzer = "";

    /** Metadata fields */
    private List<ConfigMetadataField> fields = new ArrayList<>();

    public ConfigMetadataBlock copy() {
        ConfigMetadataBlock result = new ConfigMetadataBlock();
        result.setContainerPath(containerPath);
        for (ConfigMetadataField f: fields) {
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

    public void addMetadataField(ConfigMetadataField f) {
        // If no custom analyzer specified, inherit from block
        if (f.getAnalyzer().equals(""))
            f.setAnalyzer(analyzer);
        fields.add(f);
    }

    public String getAnalyzer() {
        return analyzer;
    }

    public void setAnalyzer(String analyzer) {
        this.analyzer = analyzer;
    }

}
