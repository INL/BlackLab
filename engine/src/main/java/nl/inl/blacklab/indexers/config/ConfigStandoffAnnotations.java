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
     * If this is a span (that is, spanEndPath is not empty), this refers to the start of
     * the span.
     */
    private String tokenRefPath;

    /**
     * How to find the end of the span. If non-empty, this is a span annotation instead of
     * a regular one.
     */
    private String spanEndPath = "";

    /**
     * If this is a span, does spanEndpath refer to the last token inside the span (inclusive)
     * or the first token outside the span (exclusive)?
     */
    private boolean spanEndIsInclusive = true;

    /**
     * XPath needed to find the name of the span, if this is one (i.e. spanEndPath is non-empty).
     * E.g. for a sentence this will usually resolve to "s".
     */
    private String spanNamePath;

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

    public String getSpanEndPath() {
        return spanEndPath;
    }

    public void setSpanEndPath(String spanEndPath) {
        this.spanEndPath = spanEndPath;
    }

    public boolean isSpanEndIsInclusive() {
        return spanEndIsInclusive;
    }

    public void setSpanEndIsInclusive(boolean spanEndIsInclusive) {
        this.spanEndIsInclusive = spanEndIsInclusive;
    }

    public String getSpanNamePath() {
        return spanNamePath;
    }

    public void setSpanNamePath(String spanNamePath) {
        this.spanNamePath = spanNamePath;
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
