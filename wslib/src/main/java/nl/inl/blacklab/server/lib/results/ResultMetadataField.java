package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.MetadataField;

public class ResultMetadataField {
    private String indexName;
    private MetadataField fieldDesc;
    private boolean listValues;
    private Map<String, Integer> fieldValues;

    ResultMetadataField(String indexName, MetadataField fieldDesc, boolean listValues,
            Map<String, Integer> fieldValues) {
        this.indexName = indexName;
        this.fieldDesc = fieldDesc;
        this.listValues = listValues;
        this.fieldValues = fieldValues;
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

    public Map<String, Integer> getFieldValues() {
        return fieldValues;
    }
}
