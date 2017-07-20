package nl.inl.blacklab.index.xpath;

/** Configuration for metadata field(s). */
class ConfigMetadataField {

    /** Metadata field name (or name XPath, if forEach) */
    private String fieldName;

    /** Where to find metadata value */
    private String valuePath;

    /** If null: regular metadata field definition. Otherwise, find all nodes matching this XPath,
     *  then evaluate fieldName and valuePath as XPaths for each matching node.
     */
    private String forEachPath;

    public ConfigMetadataField() {
    }

    public ConfigMetadataField(String name, String valuePath) {
        this(name, valuePath, null);
    }

    public ConfigMetadataField(String fieldName, String valuePath, String forEachPath) {
        setFieldName(fieldName);
        setValuePath(valuePath);
        setForEachPath(forEachPath);
    }

    private void setForEachPath(String forEachPath) {
        this.forEachPath = forEachPath;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getValuePath() {
        return valuePath;
    }

    public String getForEachPath() {
        return forEachPath;
    }

    public boolean isForEach() {
        return forEachPath != null;
    }

}
