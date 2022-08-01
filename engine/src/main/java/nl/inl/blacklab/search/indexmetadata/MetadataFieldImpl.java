package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.DocValuesType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A metadata field in an index.
 */
public class MetadataFieldImpl extends FieldImpl implements MetadataField, Freezable<MetadataFieldImpl> {
    
    private static final Logger logger = LogManager.getLogger(MetadataFieldImpl.class);

    private static final int MAX_VALUE_STORE_LENGTH = 256;

    private static int maxMetadataValuesToStore = 50;

    /** Did we encounter a value that was too long to store and warn the user about it? */
    private boolean warnedAboutValueLength = false;

    private boolean keepTrackOfValues = true;

    public static void setMaxMetadataValuesToStore(int n) {
        maxMetadataValuesToStore = n;
    }

    public static int maxMetadataValuesToStore() {
        return maxMetadataValuesToStore;
    }

    /**
     * The field type: text, untokenized or numeric.
     */
    private FieldType type = FieldType.TOKENIZED;

    /**
     * The analyzer to use for indexing and querying this field.
     */
    private String analyzer = "DEFAULT";

    /**
     * When value is missing or empty this value may be used instead (whether it is
     * depends or unknownCondition).
     */
    private String unknownValue = "unknown";

    /**
     * When is the unknown value for this field used?
     */
    private UnknownCondition unknownCondition = UnknownCondition.NEVER;

    /**
     * The values this field can have. Note that this may not be the complete list;
     * check valueListComplete.
     */
    private final Map<String, Integer> values = new HashMap<>();

    /** Gives the display value corresponding to a value, if any. */
    private final Map<String, String> displayValues = new HashMap<>();

    /** Order in which to display values in select dropdown (if defined) */
    private final List<String> displayOrder = new ArrayList<>();

    /**
     * Whether or not all values are stored here.
     */
    private ValueListComplete valueListComplete = ValueListComplete.UNKNOWN;

    /**
     * The field group this field belongs to. Can be used by a generic search
     * application to generate metadata search interface.
     */
    private String group;

    /**
     * Type of UI element to show for this field. Can be used by a generic search
     * application to generate metadata search interface.
     */
    private String uiType = "";

    /**
     * If true, this instance is frozen and may not be mutated anymore.
     * Doing so anyway will throw an exception.
     */
    private boolean frozen;

    /**
     * Type of DocValues stored for this field, or NONE if no DocValues were stored.
     */
    private DocValuesType docValuesType;

    MetadataFieldImpl(String fieldName, FieldType type) {
        super(fieldName);
        this.type = type;
    }

    public void setKeepTrackOfValues(boolean keepTrackOfValues) {
        this.keepTrackOfValues = keepTrackOfValues;
    }

    @Override
    public FieldType type() {
        return type;
    }

    @Override
    public List<String> displayOrder() {
        return Collections.unmodifiableList(displayOrder);
    }

    @Override
    public String analyzerName() {
        return analyzer;
    }

    @Override
    public String unknownValue() {
        return unknownValue;
    }

    @Override
    public UnknownCondition unknownCondition() {
        return unknownCondition;
    }

    @Override
    public Map<String, Integer> valueDistribution() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public ValueListComplete isValueListComplete() {
        return valueListComplete;
    }

    @Override
    public Map<String, String> displayValues() {
        return Collections.unmodifiableMap(displayValues);
    }

    @Override
    public String group() {
        return group;
    }

    @Override
    public String uiType() {
        return uiType;
    }
    
    @Override
    public String offsetsField() {
        return name();
    }

    public DocValuesType docValuesType() {
        return docValuesType;
    }

    // Methods that mutate data
    // -------------------------------------------------
    


    @Override
    public synchronized void freeze() {
        this.frozen = true;
    }
    
    @Override
    public synchronized boolean isFrozen() {
        return this.frozen;
    }
    
    public void setAnalyzer(String analyzer) {
        if (this.analyzer == null || !this.analyzer.equals(analyzer)) {
            ensureNotFrozen();
            this.analyzer = analyzer;
        }
    }

    public void setUnknownValue(String unknownValue) {
        if (this.unknownValue == null || !this.unknownValue.equals(unknownValue)) {
            ensureNotFrozen();
            this.unknownValue = unknownValue;
        }
    }

    public void setUnknownCondition(UnknownCondition unknownCondition) {
        if (this.unknownCondition == null || !this.unknownCondition.equals(unknownCondition)) {
            ensureNotFrozen();
            this.unknownCondition = unknownCondition;
        }
    }

    public void setValues(JsonNode values) {
        ensureNotFrozen();
        this.values.clear();
        Iterator<Entry<String, JsonNode>> it = values.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> entry = it.next();
            String value = entry.getKey();
            int count = entry.getValue().asInt();
            this.values.put(value, count);
        }
    }

    public void setDisplayValues(JsonNode displayValues) {
        ensureNotFrozen();
        this.displayValues.clear();
        Iterator<Entry<String, JsonNode>> it = displayValues.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> entry = it.next();
            String value = entry.getKey();
            String displayValue = entry.getValue().textValue();
            this.displayValues.put(value, displayValue);
        }
    }

    public void setDisplayOrder(List<String> displayOrder) {
        ensureNotFrozen();
        this.displayOrder.clear();
        this.displayOrder.addAll(displayOrder);
    }

    public void setValueListComplete(boolean valueListComplete) {
        ensureNotFrozen();
        this.valueListComplete = valueListComplete ? ValueListComplete.YES : ValueListComplete.NO;
    }

    /**
     * Keep track of unique values of this field so we can store them in the
     * metadata file.
     *
     * @param value field value
     */
    public synchronized void addValue(String value) {
        if (!keepTrackOfValues)
            return;
        ensureNotFrozen();
        // If we've seen a value, assume we'll get to see all values;
        // when it turns out there's too many or they're too long,
        // we'll change the value to NO.
        if (valueListComplete == ValueListComplete.UNKNOWN)
            valueListComplete = ValueListComplete.YES;
    
        if (value.length() > MAX_VALUE_STORE_LENGTH) {
            // Value too long to store.
            valueListComplete = ValueListComplete.NO;
            if (!warnedAboutValueLength) {
                warnedAboutValueLength = true;
                logger.warn("Metadata field " + name() + " includes a value too long to store in indexmetadata.yaml (" + value.length() + " > " + MAX_VALUE_STORE_LENGTH + "). Will not store this value and will set valueListComplete to false. The value will still be indexed/stored in Lucene as normal. This warning only appears once.");
            }
            return;
        }
        if (values.containsKey(value)) {
            // Seen this value before; increment frequency
            values.put(value, values.get(value) + 1);
        } else {
            // New value; add it
            if (values.size() >= maxMetadataValuesToStore) {
                // We can't store thousands of unique values;
                // Stop storing now and indicate that there's more.
                valueListComplete = ValueListComplete.NO;
                return;
            }
            values.put(value, 1);
        }
    }

    /**
     * Remove a previously added value so we can keep track of unique 
     * values of this field correctly
     *
     * @param value field value to remove
     */
    public synchronized void removeValue(String value) {
        ensureNotFrozen();
        
        // If we've seen a value, assume we'll get to see all values;
        // when it turns out there's too many or they're too long,
        // we'll change the value to NO.
        if (valueListComplete == ValueListComplete.UNKNOWN)
            valueListComplete = ValueListComplete.YES;
    
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

    /**
     * Reset the information that is dependent on input data (i.e. list of values,
     * etc.) because we're going to (re-)index the data.
     */
    public void resetForIndexing() {
        ensureNotFrozen();
        this.values.clear();
        valueListComplete = ValueListComplete.UNKNOWN;
    }

    public void setGroup(String group) {
        ensureNotFrozen();
        this.group = group;
    }

    public void setUiType(String uiType) {
        ensureNotFrozen();
        this.uiType = uiType;
    }

    public void setDocValuesType(DocValuesType docValuesType) {
        this.docValuesType = docValuesType;
    }

}
