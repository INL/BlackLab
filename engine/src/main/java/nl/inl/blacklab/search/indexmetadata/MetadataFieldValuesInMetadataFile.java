package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is the version that is stored in the index metadata file.
 */
class MetadataFieldValuesInMetadataFile implements MetadataFieldValues {

    static class Factory implements MetadataFieldValues.Factory {
        public MetadataFieldValues create(String fieldName, FieldType fieldType) {
            return new MetadataFieldValuesInMetadataFile(fieldName);
        }
    }

    private static final Logger logger = LogManager.getLogger(MetadataFieldValuesInMetadataFile.class);

    private static final int MAX_VALUE_STORE_LENGTH = 256;

    /**
     * The values this field can have. Note that this may not be the complete list;
     * check valueListComplete.
     */
    private final Map<String, Integer> values = new HashMap<>();

    /**
     * Whether or not all values are stored here.
     */
    private ValueListComplete valueListComplete = ValueListComplete.UNKNOWN;

    /**
     * Did we encounter a value that was too long to store and warn the user about it?
     */
    private boolean warnedAboutValueLength = false;

    /**
     * Field name for use in warning message
     */
    private String fieldName;

    public MetadataFieldValuesInMetadataFile(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public boolean shouldAddValuesWhileIndexing() {
        return true;
    }

    @Override
    public Map<String, Integer> distribution() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public ValueListComplete isComplete() {
        return valueListComplete;
    }

    @Override
    public void setValues(JsonNode values) {
        this.values.clear();
        Iterator<Map.Entry<String, JsonNode>> it = values.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String value = entry.getKey();
            int count = entry.getValue().asInt();
            this.values.put(value, count);
        }
    }

    @Override
    public void setComplete(ValueListComplete complete) {
        this.valueListComplete = complete;
    }

    @Override
    public void addValue(String value) {
        // If we've seen a value, assume we'll get to see all values;
        // when it turns out there's too many or they're too long,
        // we'll change the value to NO.
        if (isComplete() == ValueListComplete.UNKNOWN)
            setComplete(ValueListComplete.YES);

        if (value.length() > MAX_VALUE_STORE_LENGTH) {
            // Value too long to store.
            setComplete(ValueListComplete.NO);
            if (!warnedAboutValueLength) {
                warnedAboutValueLength = true;
                logger.warn(
                        "Metadata field " + fieldName + " includes a value too long to store in indexmetadata.yaml ("
                                + value.length() + " > " + MAX_VALUE_STORE_LENGTH
                                + "). Will not store this value and will set valueListComplete to false. The value will still be indexed/stored in Lucene as normal. This warning only appears once.");
            }
            return;
        }
        if (values.containsKey(value)) {
            // Seen this value before; increment frequency
            values.put(value, values.get(value) + 1);
        } else {
            // New value; add it
            if (values.size() >= MetadataFieldImpl.maxMetadataValuesToStore()) {
                // We can't store thousands of unique values;
                // Stop storing now and indicate that there's more.
                valueListComplete = ValueListComplete.NO;
                return;
            }
            values.put(value, 1);
        }
    }

    @Override
    public void removeValue(String value) {
        // If we've seen a value, assume we'll get to see all values;
        // when it turns out there's too many or they're too long,
        // we'll change the value to NO.
        if (isComplete() == ValueListComplete.UNKNOWN)
            setComplete(ValueListComplete.YES);

        if (values.containsKey(value)) {
            // Seen this value before; decrement frequency
            int n = values.get(value) - 1;
            if (n > 0)
                values.put(value, n);
            else
                values.remove(value);
        } else {
            // That's weird; maybe it was a really long value, or there
            // were too many values to store. Just accept it and move on.
        }
    }

    @Override
    public void reset() {
        this.values.clear();
        valueListComplete = ValueListComplete.UNKNOWN;
    }
}
