package nl.inl.blacklab.indexers.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settings that will be used to write the indexmetadata file for any corpus we
 * create from this format.
 *
 * Stuff used by BLS and user interfaces.
 *
 * None of these settings have any impact on indexing. All fields are optional.
 */
public class ConfigCorpus {

    /** Corpus display name */
    private String displayName = "";

    /** Corpus description */
    private String description = "";

    /** May end user fetch contents of whole documents? [false] */
    private boolean contentViewable = false;

    /** What is the text direction of the script used? (e.g. LTR / RTL) */
    private TextDirection textDirection = TextDirection.LEFT_TO_RIGHT;

    /** Special field roles, such as pidField, titleField, etc. */
    Map<String, String> specialFields = new LinkedHashMap<>();

    /** How to group metadata fields */
    Map<String, ConfigMetadataFieldGroup> metadataFieldGroups = new LinkedHashMap<>();

    /** How to group annotated fields' annotations */
    Map<String, ConfigAnnotationGroups> annotationGroups = new LinkedHashMap<>();

    public ConfigCorpus copy() {
        ConfigCorpus result = new ConfigCorpus();
        result.contentViewable = contentViewable;
        result.textDirection = textDirection;
        result.specialFields.putAll(specialFields);
        for (ConfigMetadataFieldGroup g : getMetadataFieldGroups().values()) {
            result.addMetadataFieldGroup(g.copy());
        }
        for (ConfigAnnotationGroups g: getAnnotationGroups().values()) {
            result.addAnnotationGroups(g.copy());
        }
        return result;
    }

    public Map<String, String> getSpecialFields() {
        return Collections.unmodifiableMap(specialFields);
    }

    public void addSpecialField(String type, String fieldName) {
        specialFields.put(type, fieldName);
    }

    public Map<String, ConfigMetadataFieldGroup> getMetadataFieldGroups() {
        return Collections.unmodifiableMap(metadataFieldGroups);
    }

    void addMetadataFieldGroup(ConfigMetadataFieldGroup g) {
        metadataFieldGroups.put(g.getName(), g);
    }
    
    public Map<String, ConfigAnnotationGroups> getAnnotationGroups() {
        return Collections.unmodifiableMap(annotationGroups);
    }

    public void addAnnotationGroups(ConfigAnnotationGroups g) {
        annotationGroups.put(g.getName(), g);        
    }

    public boolean isContentViewable() {
        return contentViewable;
    }

    public void setContentViewable(boolean contentViewable) {
        this.contentViewable = contentViewable;
    }

    public TextDirection getTextDirection() {
        return this.textDirection;
    }

    public void setTextDirection(TextDirection textDirection) {
        this.textDirection = textDirection;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ConfigCorpus [displayName=" + displayName + "]";
    }

}
