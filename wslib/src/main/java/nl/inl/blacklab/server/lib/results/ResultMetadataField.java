package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.MetadataField;

public class ResultMetadataField {
    private String indexName;
    private MetadataField fieldDesc;
    private boolean listValues;
    private Map<String, Long> fieldValues;
    private boolean valueListComplete;

    ResultMetadataField(String indexName, MetadataField fieldDesc, boolean listValues,
            Map<String, Long> fieldValues, boolean valueListComplete) {
        this.indexName = indexName;
        this.fieldDesc = fieldDesc;
        this.listValues = listValues;
        this.fieldValues = fieldValues;
        this.valueListComplete = valueListComplete;
    }

    public String getIndexName() {
        return indexName;
    }

    public MetadataField getFieldDesc() {
        return fieldDesc;
    }

    public boolean isListValues() {
        return listValues;
    }

    public Map<String, Long> getFieldValues() {
        return fieldValues;
    }

    public boolean isValueListComplete() {
        return valueListComplete;
    }
}
