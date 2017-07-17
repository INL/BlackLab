package nl.inl.blacklab.index.xpath;

/**
 * Configuration for a single annotation ("property") of an
 * annotated field ("complex field").
 */
class ConfigAnnotation {

	/** Annotation name (or namePath if it's a forEach) */
    private String name;

    /** Where to find body text */
    private String valuePath;

	public ConfigAnnotation(String name, String valuePath) {
        setName(name);
        setValuePath(valuePath);
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
        this.valuePath = ConfigInputFormat.relXPath(valuePath);
    }


}