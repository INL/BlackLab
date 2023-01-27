package nl.inl.blacklab.search.indexmetadata;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.Bits;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.DocValuesUtil;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is the version that is determined from the Lucene index via DocValues.
 */
class MetadataFieldValuesFromIndex implements MetadataFieldValues {

//    private static final Logger logger = LogManager.getLogger(MetadataFieldValuesFromIndex.class);

    static class Factory implements MetadataFieldValues.Factory {

        private final BlackLabIndex index;

        Factory(BlackLabIndex index) {
            this.index = index;
        }

        public MetadataFieldValues create(String fieldName, FieldType fieldType) {
            return new MetadataFieldValuesFromIndex(index.reader(), fieldName, fieldType == FieldType.NUMERIC);
        }
    }

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
    private final String fieldName;

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
            // OPT: is this worth parallellizing?
            for (LeafReaderContext rc : reader.leaves()) {
                LeafReader r = rc.reader();
                Bits liveDocs = r.getLiveDocs();
                getDocValues(r, isNumeric, liveDocs);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void getDocValues(LeafReader r, boolean numeric, Bits liveDocs) throws IOException {
        DocIdSetIterator dv = DocValuesUtil.docValuesIterator(r, fieldName, numeric);
        if (dv != null) { // If null, the documents in this segment do not contain any values for this field
            while (true) {
                int docId = dv.nextDoc();
                if (docId == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                if (liveDocs == null || liveDocs.get(docId)) { // not deleted?
                    String key = DocValuesUtil.getCurrentValueAsString(dv);
                    if (key != null) // if null, there's no value for this field in this document
                        addToValueMap(key);
                }
            }
        }
    }

    private void addToValueMap(String key) {
        if (isComplete() == ValueListComplete.UNKNOWN)
            valueListComplete = ValueListComplete.YES;

        if (values.containsKey(key)) {
            // Seen this value before; increment frequency
            values.compute(key, (__, value) -> value == null ? 1 : value + 1);
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
