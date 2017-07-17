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

    /** Punctuation between words (or null if we don't need/want to capture this) */
    private String punctPath = null;

    /** Annotations on our words */
    private List<ConfigAnnotation> annotations = new ArrayList<>();

    /** Inline tags within body text */
    private List<ConfigInlineTag> inlineTags = new ArrayList<>();

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setContainerPath(String containerPath) {
        this.containerPath = ConfigInputFormat.relXPath(containerPath);
    }

    public void setWordsPath(String wordsPath) {
        this.wordsPath = ConfigInputFormat.relXPath(wordsPath);
    }

    public void setPunctPath(String punctPath) {
		this.punctPath = ConfigInputFormat.relXPath(punctPath);
	}

    public void addInlineTag(ConfigInlineTag inlineTag) {
        this.inlineTags.add(inlineTag);
    }

    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.add(annotation);
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

	public String getPunctPath() {
		return punctPath;
	}

    public List<ConfigInlineTag> getInlineTags() {
        return inlineTags;
    }

    public List<ConfigAnnotation> getAnnotations() {
        return Collections.unmodifiableList(annotations);
    }

}