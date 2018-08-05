package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.search.indexmetadata.nint.MetadataField;

/**
 * A metadata field in an index.
 */
public class MetadataFieldImpl extends FieldImpl implements MetadataField {
    private static final int MAX_METADATA_VALUES_TO_STORE = 50;

    /**
     * The field type: text, untokenized or numeric.
     */
    protected FieldType type = FieldType.TOKENIZED;

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
    private Map<String, Integer> values = new HashMap<>();

    /** Gives the display value corresponding to a value, if any. */
    private Map<String, String> displayValues = new HashMap<>();

    /** Order in which to display values in select dropdown (if defined) */
    private List<String> displayOrder = new ArrayList<>();

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

    public MetadataFieldImpl(String fieldName, FieldType type) {
        super(fieldName);
        this.type = type;
    }

    public FieldType type() {
        return type;
    }

    public List<String> displayOrder() {
        return Collections.unmodifiableList(displayOrder);
    }

    public String analyzerName() {
        return analyzer;
    }

    public String unknownValue() {
        return unknownValue;
    }

    public UnknownCondition unknownCondition() {
        return unknownCondition;
    }

    public Map<String, Integer> valueDistribution() {
        return Collections.unmodifiableMap(values);
    }

    public ValueListComplete isValueListComplete() {
        return valueListComplete;
    }

    public Map<String, String> displayValues() {
        return Collections.unmodifiableMap(displayValues);
    }

    public String group() {
        return group;
    }

    public String uiType() {
        return uiType;
    }

    public void setAnalyzer(String analyzer) {
        this.analyzer = analyzer;
    }

    public void setUnknownValue(String unknownValue) {
        this.unknownValue = unknownValue;
    }

    public void setUnknownCondition(UnknownCondition unknownCondition) {
        this.unknownCondition = unknownCondition;
    }

    public void setValues(JsonNode values) {
        this.values.clear();
        Iterator<Entry<String, JsonNode>> it = values.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> entry = it.next();
            String value = entry.getKey();
            int count = entry.getValue().intValue();
            this.values.put(value, count);
        }
    }

    public void setDisplayValues(JsonNode displayValues) {
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
        this.displayOrder.clear();
        this.displayOrder.addAll(displayOrder);
    }

    public void setValueListComplete(boolean valueListComplete) {
        this.valueListComplete = valueListComplete ? ValueListComplete.YES : ValueListComplete.NO;
    }

    /**
     * Keep track of unique values of this field so we can store them in the
     * metadata file.
     *
     * @param value field value
     */
    public void addValue(String value) {
        // If we've seen a value, assume we'll get to see all values;
        // when it turns out there's too many or they're too long,
        // we'll change the value to NO.
        if (valueListComplete == ValueListComplete.UNKNOWN)
            valueListComplete = ValueListComplete.YES;
    
        if (value.length() > 100) {
            // Value too long to store.
            valueListComplete = ValueListComplete.NO;
            return;
        }
        if (values.containsKey(value)) {
            // Seen this value before; increment frequency
            values.put(value, values.get(value) + 1);
        } else {
            // New value; add it
            if (values.size() >= MAX_METADATA_VALUES_TO_STORE) {
                // We can't store thousands of unique values;
                // Stop storing now and indicate that there's more.
                valueListComplete = ValueListComplete.NO;
                return;
            }
            values.put(value, 1);
        }
    }

    /**
     * Reset the information that is dependent on input data (i.e. list of values,
     * etc.) because we're going to (re-)index the data.
     */
    public void resetForIndexing() {
        this.values.clear();
        valueListComplete = ValueListComplete.UNKNOWN;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setUiType(String uiType) {
        this.uiType = uiType;
    }

}
