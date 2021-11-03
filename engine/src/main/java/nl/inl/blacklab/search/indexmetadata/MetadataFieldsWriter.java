package nl.inl.blacklab.search.indexmetadata;

/**
 * Interface for MetadataFields objects while indexing.
 */
public interface MetadataFieldsWriter extends MetadataFields {
    
    MetadataField register(String fieldName);
    
    void clearMetadataGroups();
    
    void putMetadataGroup(String name, MetadataFieldGroupImpl metadataGroup);    
    
    void setSpecialField(String specialFieldType, String fieldName);

    void ensureFieldExists(String name);

    void put(String fieldName, MetadataFieldImpl fieldDesc);

    void setDefaultAnalyzerName(String name);

    void clearSpecialFields();

    void setDefaultUnknownValue(String value);

    void setDefaultUnknownCondition(String unknownCondition);

    void resetForIndexing();

}
