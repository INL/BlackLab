package nl.inl.blacklab.search.indexmetadata;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is either stored in the index metadata file, or
 * determined from the Lucene index via DocValues.
 */
public interface MetadataFieldValues {

    MetadataFieldValues truncate(long maxValues);

    boolean canTruncateTo(long maxValues);

    @FunctionalInterface
    interface Factory {
        MetadataFieldValues create(String fieldName, FieldType fieldType, long limitValues);
    }

    TruncatableFreqList valueList();

    void setValues(JsonNode values);

    void setComplete(boolean complete);

    void addValue(String value);

    void removeValue(String value);

    void reset();
}
