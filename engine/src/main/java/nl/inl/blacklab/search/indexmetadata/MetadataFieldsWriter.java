package nl.inl.blacklab.search.indexmetadata;

import java.util.Map;

/**
 * Interface for MetadataFields objects while indexing.
 */
public interface MetadataFieldsWriter extends MetadataFields {
    
    MetadataField register(String fieldName);
    
    void setMetadataGroups(Map<String, MetadataFieldGroupImpl> metadataGroups);

    /**
     * @deprecated use indexmetadata.custom().put(propName, ...) instead
     */
    @Deprecated
    void setSpecialField(String specialFieldType, String fieldName);

    void put(String fieldName, MetadataFieldImpl fieldDesc);

    void setDefaultAnalyzer(String name);

    /**
     * @deprecated use indexmetadata.custom().put(propName, null) instead
     */
    @Deprecated
    void clearSpecialFields();

    void setDefaultUnknownValue(String value);

    void setDefaultUnknownCondition(String unknownCondition);

    void resetForIndexing();

}
