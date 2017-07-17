package nl.inl.blacklab.index.xpath;

/**
 * Configuration for a single annotation ("property") of an
 * annotated field ("complex field").
 */
class ConfigInlineTag {

	/** XPath to the inline tag, relative to the container element */
    private String tagPath;

    public ConfigInlineTag() {
    }

	public ConfigInlineTag(String path) {
        setTagPath(path);
    }

    public String getTagPath() {
        return tagPath;
    }

    public void setTagPath(String tagPath) {
        this.tagPath = ConfigInputFormat.relXPath(tagPath);
    }

}