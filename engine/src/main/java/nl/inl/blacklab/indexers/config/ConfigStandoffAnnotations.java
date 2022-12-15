package nl.inl.blacklab.indexers.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for a block of standoff annotations (annotations that don't
 * reside under the word tag but elsewhere in the document).
 */
public class ConfigStandoffAnnotations implements ConfigWithAnnotations {

    /**
     * Path to the elements containing the values to index (values may apply to
     * multiple token positions)
     */
    private String path;

    /**
     * Unique id of the token position(s) to index these values at. A uniqueId must
     * be defined for words.
     */
    private String tokenRefPath;

    /** The annotations to index at the referenced token positions. */
    private final Map<String, ConfigAnnotation> annotations = new LinkedHashMap<>();

    public ConfigStandoffAnnotations() {
    }

    public ConfigStandoffAnnotations(String path, String tokenRefPath) {
        this.path = path;
        this.tokenRefPath = tokenRefPath;
    }

    public void validate() {
        String t = "standoff annotations";
        ConfigInputFormat.req(path, t, "path");
        ConfigInputFormat.req(tokenRefPath, t, "tokenRefPath");
        for (ConfigAnnotation a : annotations.values())
            a.validate();
    }

    public ConfigStandoffAnnotations copy() {
        ConfigStandoffAnnotations result = new ConfigStandoffAnnotations(path, tokenRefPath);
        for (ConfigAnnotation a : annotations.values()) {
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

    /**
     * @deprecated renamed to { @link {@link #getTokenRefPath() }
     */
    @Deprecated
    public String getRefTokenPositionIdPath() {
        return getTokenRefPath();
    }

    /**
     * @deprecated renamed to { @link {@link #setTokenRefPath(String) }
     */
    @Deprecated
    public void setRefTokenPositionIdPath(String path) {
        setTokenRefPath(path);
    }

    public String getTokenRefPath() {
        return tokenRefPath;
    }

    public void setTokenRefPath(String path) {
        this.tokenRefPath = path;
    }

    @Override
    public Map<String, ConfigAnnotation> getAnnotations() {
        return annotations;
    }
    
    @Override
    public Map<String, ConfigAnnotation> getAnnotationsFlattened() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.put(annotation.getName(), annotation);
    }

    @Override
    public String toString() {
        return "ConfigStandoffAnnotations [path=" + path + "]";
    }
    
}
