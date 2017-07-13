package nl.inl.blacklab.index.xpath;

/**
 * Configuration for a single annotation ("property") of an
 * annotated field ("complex field").
 */
class ConfigAnnotation {
    private String name;

    private String xpValue;

    public ConfigAnnotation(String name, String xpValue) {
        setName(name);
        setXpValue(xpValue);
//        if (!this.xpValue.endsWith("/text()"))
//            this.xpValue += "/text()";
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

    public void setXpValue(String xpValue) {
        this.xpValue = ConfigInputFormat.relXPath(xpValue);
    }

}