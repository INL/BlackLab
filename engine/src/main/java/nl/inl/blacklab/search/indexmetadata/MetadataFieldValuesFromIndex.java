package nl.inl.blacklab.search.indexmetadata;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.search.BlackLabIndex;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is the version that is determined from the Lucene index via DocValues.
 */
class MetadataFieldValuesFromIndex implements MetadataFieldValues {

    static class Factory implements MetadataFieldValues.Factory {

        private final BlackLabIndex index;

        Factory(BlackLabIndex index) {
            this.index = index;
        }

        public MetadataFieldValues create(String fieldName, FieldType fieldType) {
            return new MetadataFieldValuesFromIndex(index.reader(), fieldName, fieldType == FieldType.NUMERIC);
        }
    }

    private static final Logger logger = LogManager.getLogger(MetadataFieldValuesFromIndex.class);

    /**
     * The values this field can have. Note that this may not be the complete list;
     * check valueListComplete.
     */
    private final Map<String, Integer> values = new HashMap<>();

    private final boolean isNumeric;

    /**
     * Whether or not all values are stored here.
     */
    private ValueListComplete valueListComplete = ValueListComplete.UNKNOWN;

    /**
     * Field name for use in warning message
     */
    private String fieldName;

    public MetadataFieldValuesFromIndex(IndexReader reader, String fieldName, boolean isNumeric) {
        this.fieldName = fieldName;
        this.isNumeric = isNumeric;
        determineValueDistribution(reader);
    }

    @Override
    public boolean shouldAddValuesWhileIndexing() {
        return false;
    }

    private void determineValueDistribution(IndexReader reader) {
        try {
            // TODO: parallellize this?
            if (isNumeric) {
                for (LeafReaderContext rc : reader.leaves()) {
                    LeafReader r = rc.reader();
                    getNumericDocValues(r);
                }
            } else {
                for (LeafReaderContext rc : reader.leaves()) {
                    LeafReader r = rc.reader();
                    getStringDocValues(r);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addValueInternal(String key) {
        if (isComplete() == ValueListComplete.UNKNOWN)
            valueListComplete = ValueListComplete.YES;

        if (values.containsKey(key)) {
            // Seen this value before; increment frequency
            values.compute(key, (__, value) -> value + 1);
        } else {
            // New value; add it
            if (values.size() >= MetadataFieldImpl.maxMetadataValuesToStore()) {
                // We don't want to store thousands of unique values;
                // Stop storing now and indicate that there's more.
                valueListComplete = ValueListComplete.NO;
            } else {
                values.put(key, 1);
            }
        }
    }

    private void getStringDocValues(LeafReader r) throws IOException {
        // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
        SortedSetDocValues sdv = r.getSortedSetDocValues(fieldName);
        if (sdv != null) {
            while (true) {
                int docId = sdv.nextDoc();
                if (docId == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                if (sdv.getValueCount() > 0) {
                    // NOTE: we only count the first value stored (for backward compatibility)
                    // TODO: pros/cons of changing this?
                    addValueInternal(sdv.lookupOrd(0).utf8ToString());
                }
            }
        } else {
            SortedDocValues dv = r.getSortedDocValues(fieldName);
            if (dv != null) {
                while (true) {
                    int docId = dv.nextDoc();
                    if (docId == DocIdSetIterator.NO_MORE_DOCS)
                        break;
                    addValueInternal(dv.binaryValue().utf8ToString());
                }
            }
        }
    }

    private void getNumericDocValues(LeafReader r) throws IOException {
        // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
        NumericDocValues dv = r.getNumericDocValues(fieldName);
        if (dv != null) {
            while (true) {
                int docId = dv.nextDoc();
                if (docId == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                addValueInternal(Long.toString(dv.longValue()));
            }
        }
    }

    @Override
    public Map<String, Integer> distribution() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public ValueListComplete isComplete() {
        return ValueListComplete.YES;
    }

    @Override
    public void setValues(JsonNode values) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void setComplete(ValueListComplete complete) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void addValue(String value) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void removeValue(String value) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }
}
