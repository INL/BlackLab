package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.List;

/** Configuration for a block of metadata fields. */
class ConfigMetadataBlock {

    /** Where the block can be found */
    private String containerPath;

    /** Metadata fields */
    private List<ConfigMetadataField> metadataFields = new ArrayList<>();

    public String getContainerPath() {
        return containerPath;
    }

    public void setContainerPath(String containerPath) {
        this.containerPath = containerPath;
    }

    public List<ConfigMetadataField> getMetadataFields() {
        return metadataFields;
    }

    public void addMetadataField(ConfigMetadataField f) {
        metadataFields.add(f);
    }

}
