package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for a single annotation ("property") of an
 * annotated field ("complex field").
 */
class ConfigAnnotation {

	/** Annotation name, or forEach (or name XPath, if forEach) */
    private String name;

    /** If specified, all other XPath expression are relative to this */
    private String basePath;

    /** Where to find body text */
    private String valuePath;

    /** If null: regular annotation definition. Otherwise, find all nodes matching this XPath,
     *  then evaluate name and valuePath as XPaths for each matching node, adding a subannotation
     *  value for each.
     *  NOTE: forEach is only supported for subannotations, because all main annotations (complex field
     *  properties) need to be known from the start.
     */
    private String forEachPath;

    /** XPaths to capture the value of, to substitute for $1-$9 in valuePath */
    private List<String> captureValuePaths = new ArrayList<>();

    /** Our subannotations. Note that only 1 level of subannotations is processed for now
     *  (i.e. there's no subsubannotations), although we could process more levels if desired. */
    private List<ConfigAnnotation> subAnnotations = new ArrayList<>();

    public ConfigAnnotation() {
    }

    public ConfigAnnotation(String name, String valuePath) {
        this(name, valuePath, null);
    }

	public ConfigAnnotation(String name, String valuePath, String forEachPath) {
        setName(name);
        setValuePath(valuePath);
        setForEachPath(forEachPath);
    }

	public String getName() {
        return name;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public List<String> getCaptureValuePaths() {
        return Collections.unmodifiableList(captureValuePaths);
    }

    public void addCaptureValuePath(String captureValuePath) {
        captureValuePaths.add(captureValuePath);
    }

    public List<ConfigAnnotation> getSubAnnotations() {
        return Collections.unmodifiableList(subAnnotations);
    }

    public void addSubAnnotation(ConfigAnnotation subAnnotation) {
        subAnnotations.add(subAnnotation);
    }

    public String getForEachPath() {
        return forEachPath;
    }

    public void setForEachPath(String forEachPath) {
        this.forEachPath = forEachPath;
    }

    public boolean isForEach() {
        return forEachPath != null;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

}