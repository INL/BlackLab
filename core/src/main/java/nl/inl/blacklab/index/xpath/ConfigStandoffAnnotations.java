package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a block of standoff annotations (annotations that
 * don't reside under the word tag but elsewhere in the document).
 */
class ConfigStandoffAnnotations {

	/** Path to the elements containing the values to index
	 *  (values may apply to multiple token positions)
	 */
    private String path;

    /** Unique id of the token position(s) to index these values at.
     *  A uniqueId must be defined for words.
     */
    private String refTokenPositionIdPath;

    /** The annotations to index at the referenced token positions. */
    private List<ConfigAnnotation> annotations = new ArrayList<>();

    public ConfigStandoffAnnotations() {
    }

    public ConfigStandoffAnnotations(String path, String refTokenPositionIdPath) {
        this.path = path;
        this.refTokenPositionIdPath = refTokenPositionIdPath;
    }

    public ConfigStandoffAnnotations copy() {
        ConfigStandoffAnnotations result = new ConfigStandoffAnnotations(path, refTokenPositionIdPath);
        for (ConfigAnnotation a: annotations) {
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

    public List<ConfigAnnotation> getAnnotations() {
        return annotations;
    }

    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.add(annotation);
    }

}