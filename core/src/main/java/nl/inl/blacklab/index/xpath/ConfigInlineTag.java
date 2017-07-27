package nl.inl.blacklab.index.xpath;

/**
 * Configuration for an XML element occurring in an annotated field.
 */
class ConfigInlineTag {

	/** XPath to the inline tag, relative to the container element */
    private String path;

    public ConfigInlineTag() {
    }

	public ConfigInlineTag(String path) {
        setPath(path);
    }

    public void validate() {
        ConfigInputFormat.req(path, "inline tag", "path");
    }

    public ConfigInlineTag copy() {
        return new ConfigInlineTag(path);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

}