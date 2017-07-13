package nl.inl.blacklab.index.xpath;

/** Configuration for metadata fields. */
class ConfigMetadataField {

    private String fieldName;

    /** Where to find body text */
    private String xpValue;

    /** If null: regular metadata field. Otherwise, find all nodes matching this XPath,
     *  then evaluate fieldName and xpValue as XPaths for each matching node.
     */
    private String xpForEach;

    public ConfigMetadataField(String fieldName, String xpValue, String xpForEach) {
        setFieldName(fieldName);
        setXPathValue(xpValue);
        setXPathForEach(xpForEach);
    }

    private void setXPathForEach(String xpForEach) {
        this.xpForEach = xpForEach;
    }

    public ConfigMetadataField(String fieldName, String xpValue) {
        this(fieldName, xpValue, null);
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setXPathValue(String xpValue) {
        this.xpValue = ConfigInputFormat.relXPath(xpValue);
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getXPathValue() {
        return xpValue;
    }

    public String getXPathForEach() {
        return xpForEach;
    }

    public boolean isForEach() {
        return xpForEach != null;
    }

}
