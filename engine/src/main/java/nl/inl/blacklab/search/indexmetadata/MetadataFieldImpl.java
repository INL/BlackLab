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

    private static int maxMetadataValuesToStore = 50;

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

    /** Gives the display value corresponding to a value, if any. */
    private final Map<String, String> displayValues = new HashMap<>();

    /** Order in which to display values in select dropdown (if defined) */
    private final List<String> displayOrder = new ArrayList<>();

    /**
     * Values for this field and their frequencies.
     */
    private MetadataFieldValues values;

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

    MetadataFieldImpl(String fieldName, FieldType type, MetadataFieldValues.Factory factory) {
        this(fieldName, type, factory.create(fieldName, type));
    }

    MetadataFieldImpl(String fieldName, FieldType type, MetadataFieldValues values) {
        super(fieldName);
        this.type = type;
        this.values = values;
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
        return values.distribution();
    }

    @Override
    public ValueListComplete isValueListComplete() {
        return values.isComplete();
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
        if (this.values.shouldAddValuesWhileIndexing()) {
            ensureNotFrozen();
            this.values.setValues(values);
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
        if (this.values.shouldAddValuesWhileIndexing()) {
            ensureNotFrozen();
            this.values.setComplete(valueListComplete ? ValueListComplete.YES : ValueListComplete.NO);
        }
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
        values.addValue(value);
    }

    /**
     * Remove a previously added value so we can keep track of unique 
     * values of this field correctly
     *
     * @param value field value to remove
     */
    public synchronized void removeValue(String value) {
        ensureNotFrozen();
        values.removeValue(value);
    }

    /**
     * Reset the information that is dependent on input data (i.e. list of values,
     * etc.) because we're going to (re-)index the data.
     */
    public void resetForIndexing() {
        ensureNotFrozen();
        values.reset();
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
