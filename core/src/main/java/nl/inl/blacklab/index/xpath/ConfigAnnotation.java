package nl.inl.blacklab.index.xpath;

/**
 * Configuration for a single annotation ("property") of an
 * annotated field ("complex field").
 */
class ConfigAnnotation {
	
	/** Annotation name (or name XPath if it's a forEach) */
    private String name;

    /** Where to find body text */
    private String xpValue;

	public ConfigAnnotation(String name, String xpValue) {
        setName(name);
        setXPathValue(xpValue);
    }

	public String getName() {
        return name;
    }

    public String getXPathValue() {
        return xpValue;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setXPathValue(String xpValue) {
        this.xpValue = ConfigInputFormat.relXPath(xpValue);
    }


}