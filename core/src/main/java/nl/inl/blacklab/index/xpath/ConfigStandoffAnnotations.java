package nl.inl.blacklab.index.xpath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for a block of standoff annotations (annotations that
 * don't reside under the word tag but elsewhere in the document).
 */
class ConfigStandoffAnnotations implements ConfigWithAnnotations {

	/** Path to the elements containing the values to index
	 *  (values may apply to multiple token positions)
	 */
    private String path;

    /** Unique id of the token position(s) to index these values at.
     *  A uniqueId must be defined for words.
     */
    private String refTokenPositionIdPath;

    /** The annotations to index at the referenced token positions. */
    private Map<String, ConfigAnnotation> annotations = new LinkedHashMap<>();

    public ConfigStandoffAnnotations() {
    }

    public ConfigStandoffAnnotations(String path, String refTokenPositionIdPath) {
        this.path = path;
        this.refTokenPositionIdPath = refTokenPositionIdPath;
    }

    public ConfigStandoffAnnotations copy() {
        ConfigStandoffAnnotations result = new ConfigStandoffAnnotations(path, refTokenPositionIdPath);
        for (ConfigAnnotation a: annotations.values()) {
            result.addAnnotation(a.copy());
        }
        return result;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRefTokenPositionIdPath() {
        return refTokenPositionIdPath;
    }

    public void setRefTokenPositionIdPath(String refTokenPositionIdPath) {
        this.refTokenPositionIdPath = refTokenPositionIdPath;
    }

    @Override
    public Map<String, ConfigAnnotation> getAnnotations() {
        return annotations;
    }

    @Override
    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.put(annotation.getName(), annotation);
    }

}