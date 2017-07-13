package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Configuration for annotated ("complex") fields in our XML */
class ConfigAnnotatedField {

    private String fieldName;

    /** Where to find body text */
    private String xpBody;

    /** Words within body text */
    private String xpWords;

    /** Inline tags within body text */
    private List<String> xpsInlineTags = new ArrayList<>();

    /** Annotations on our words */
    private List<ConfigAnnotation> annotations = new ArrayList<>();

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setXPathBody(String xpBody) {
        this.xpBody = ConfigInputFormat.relXPath(xpBody);
    }

    public void setXPathWords(String xpWords) {
        this.xpWords = ConfigInputFormat.relXPath(xpWords);
    }

    public void addXPathInlineTag(String xpInlineTag) {
        this.xpsInlineTags.add(ConfigInputFormat.relXPath(xpInlineTag));
    }

    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.add(annotation);
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getXPathBody() {
        return xpBody;
    }

    public String getXPathWords() {
        return xpWords;
    }

    public List<String> getXPathsInlineTag() {
        return xpsInlineTags;
    }

    public List<ConfigAnnotation> getAnnotations() {
        return Collections.unmodifiableList(annotations);
    }

}