package nl.inl.blacklab.search.indexmetadata;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is either stored in the index metadata file, or
 * determined from the Lucene index via DocValues.
 */
interface MetadataFieldValues {

    @FunctionalInterface
    interface Factory {
        MetadataFieldValues create(String fieldName, FieldType fieldType);
    }

    boolean shouldAddValuesWhileIndexing();

    Map<String, Integer> distribution();

    ValueListComplete isComplete();

    void setValues(JsonNode values);

    void setComplete(ValueListComplete complete);

    void addValue(String value);

    void removeValue(String value);

    void reset();
}
