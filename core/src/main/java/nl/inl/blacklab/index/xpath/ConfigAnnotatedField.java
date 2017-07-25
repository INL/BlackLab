package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configuration for annotated ("complex") fields in our XML */
public class ConfigAnnotatedField implements ConfigWithAnnotations {

    private String name;

    /** How to display the field in the interface (optional) */
    private String displayName = "";

    /** How to describe the field in the interface (optional) */
    private String description = "";

    /** Where to find this field's annotated text */
    private String containerPath = ".";

    /** Words within body text */
    private String wordsPath;

    /** Unique id that will map to this token position */
    private String tokenPositionIdPath;

    /** Punctuation between words (or null if we don't need/want to capture this) */
    private String punctPath = null;

    /** Annotations on our words */
    private Map<String, ConfigAnnotation> annotations = new LinkedHashMap<>();

    /** Annotations on our words, defined elsewhere in the document */
    private List<ConfigStandoffAnnotations> standoffAnnotations = new ArrayList<>();

    /** Inline tags within body text */
    private List<ConfigInlineTag> inlineTags = new ArrayList<>();

    ConfigAnnotatedField(String fieldName) {
        setName(fieldName);
    }

    public ConfigAnnotatedField copy() {
        ConfigAnnotatedField result = new ConfigAnnotatedField(name);
        result.setDisplayName(displayName);
        result.setDescription(description);
        result.setContainerPath(containerPath);
        result.setWordsPath(wordsPath);
        result.setTokenPositionIdPath(tokenPositionIdPath);
        result.setPunctPath(punctPath);
        for (ConfigAnnotation a: annotations.values())
            result.addAnnotation(a.copy());
        for (ConfigStandoffAnnotations a: standoffAnnotations)
            result.addStandoffAnnotation(a.copy());
        for (ConfigInlineTag t: inlineTags)
            result.addInlineTag(t.copy());
        return result;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setContainerPath(String containerPath) {
        this.containerPath = containerPath;
    }

    public void setWordsPath(String wordsPath) {
        this.wordsPath = wordsPath;
    }

    public void setTokenPositionIdPath(String tokenPositionIdPath) {
        this.tokenPositionIdPath = tokenPositionIdPath;
    }

    public void setPunctPath(String punctPath) {
		this.punctPath = punctPath;
	}

    public void addInlineTag(ConfigInlineTag inlineTag) {
        this.inlineTags.add(inlineTag);
    }

    public void addInlineTag(String path) {
        inlineTags.add(new ConfigInlineTag(path));
    }

    @Override
    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.put(annotation.getName(), annotation);
    }

    public void addStandoffAnnotation(ConfigStandoffAnnotations standoff) {
        standoffAnnotations.add(standoff);
    }

    public String getName() {
        return name;
    }

    public String getContainerPath() {
        return containerPath;
    }

    public String getWordsPath() {
        return wordsPath;
    }

    public String getTokenPositionIdPath() {
        return tokenPositionIdPath;
    }

	public String getPunctPath() {
		return punctPath;
	}

    public List<ConfigInlineTag> getInlineTags() {
        return inlineTags;
    }

    @Override
    public Map<String, ConfigAnnotation> getAnnotations() {
        return Collections.unmodifiableMap(annotations);
    }

    public List<ConfigStandoffAnnotations> getStandoffAnnotations() {
        return Collections.unmodifiableList(standoffAnnotations);
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

}