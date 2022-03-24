package nl.inl.blacklab.tools.frequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Configuration for making frequency lists
 */
class ConfigFreqList {

    /**
     * A unique name that will be used as output file name
     */
    String name = "";

    /**
     * Annotations to make frequency lists for
     */
    private List<String> annotations;

    /**
     * Metadata fields to take into account (e.g. year for frequencies per year)
     */
    private List<String> metadataFields = Collections.emptyList();

    public String getName() {
        return name;
    }

    public String getReportName() {
        return name.isEmpty() ? generateName() : name;
    }

    private String generateName() {
        List<String> parts = new ArrayList<>();
        parts.addAll(annotations);
        parts.addAll(metadataFields);
        return StringUtils.join(parts, "-");
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public List<String> getMetadataFields() {
        return metadataFields;
    }

    public void setMetadataFields(List<String> metadataFields) {
        this.metadataFields = metadataFields;
    }

    @Override
    public String toString() {
        return "ConfigFreqList{" +
                "name='" + name + '\'' +
                ", annotations=" + annotations +
                ", metadataFields=" + metadataFields +
                '}';
    }
}
