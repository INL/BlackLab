package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Configuration for annotated ("complex") fields in our XML */
class ConfigAnnotatedField {

    private String fieldName;

    /** Where to find this field's annotated text */
    private String containerPath = ".";

    /** Words within body text */
    private String wordsPath;

    /** Unique id that will map to this token position */
    private String tokenPositionIdPath;

    /** Punctuation between words (or null if we don't need/want to capture this) */
    private String punctPath = null;

    /** Annotations on our words */
    private List<ConfigAnnotation> annotations = new ArrayList<>();

    /** Annotations on our words, defined elsewhere in the document */
    private List<ConfigStandoffAnnotations> standoffAnnotations = new ArrayList<>();

    /** Inline tags within body text */
    private List<ConfigInlineTag> inlineTags = new ArrayList<>();

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
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

    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.add(annotation);
    }

    public void addStandoffAnnotation(ConfigStandoffAnnotations standoff) {
        standoffAnnotations.add(standoff);
    }

    public String getFieldName() {
        return fieldName;
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

    public List<ConfigAnnotation> getAnnotations() {
        return Collections.unmodifiableList(annotations);
    }

    public List<ConfigStandoffAnnotations> getStandoffAnnotations() {
        return Collections.unmodifiableList(standoffAnnotations);
    }

}