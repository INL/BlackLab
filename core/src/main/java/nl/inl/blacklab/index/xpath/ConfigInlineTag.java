package nl.inl.blacklab.index.xpath;

/**
 * Configuration for an XML element occurring in an annotated field.
 */
class ConfigInlineTag {

	/** XPath to the inline tag, relative to the container element */
    private String tagPath;

    public ConfigInlineTag() {
    }

	public ConfigInlineTag(String path) {
        setTagPath(path);
    }

    public ConfigInlineTag copy() {
        return new ConfigInlineTag(tagPath);
    }

    public String getTagPath() {
        return tagPath;
    }

    public void setTagPath(String tagPath) {
        this.tagPath = tagPath;
    }

}