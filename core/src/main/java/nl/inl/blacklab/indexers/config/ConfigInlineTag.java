package nl.inl.blacklab.indexers.config;

/**
 * Configuration for an XML element occurring in an annotated field.
 */
public class ConfigInlineTag {

    /** XPath to the inline tag, relative to the container element */
    private String path;

    /**
     * (optional) How to display this inline tag when viewing document in the
     * frontend (used as CSS class for this inline tag in generated XSLT; there are
     * several predefined classes such as sentence, paragraph, line-beginning,
     * page-beginning)
     */
    private String displayAs = "";

    public ConfigInlineTag() {
    }

    public ConfigInlineTag(String path, String displayAs) {
        setPath(path);
        setDisplayAs(displayAs);
    }

    public void validate() {
        ConfigInputFormat.req(path, "inline tag", "path");
    }

    public ConfigInlineTag copy() {
        return new ConfigInlineTag(path, displayAs);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDisplayAs() {
        return displayAs;
    }

    public void setDisplayAs(String displayAs) {
        this.displayAs = displayAs;
    }

    @Override
    public String toString() {
        return "ConfigInlineTag [displayAs=" + displayAs + "]";
    }

    
}
