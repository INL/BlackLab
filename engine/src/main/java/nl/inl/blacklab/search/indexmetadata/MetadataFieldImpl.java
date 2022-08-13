package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.DocValuesType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A metadata field in an index.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataFieldImpl extends FieldImpl implements MetadataField, Freezable<MetadataFieldImpl> {
    
    private static final Logger logger = LogManager.getLogger(MetadataFieldImpl.class);

    private static int maxMetadataValuesToStore = 50;

    @XmlTransient
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
     * Values for this field and their frequencies.
     */
    @XmlTransient
    private MetadataFieldValues values;

    /**
     * The field group this field belongs to. Can be used by a generic search
     * application to generate metadata search interface.
     */
    private String group;

    /**
     * If true, this instance is frozen and may not be mutated anymore.
     * Doing so anyway will throw an exception.
     */
    @XmlTransient
    private boolean frozen;

    /**
     * Type of DocValues stored for this field (numeric, sorted or sorted set).
     *
     * All metadata fielas should have doc values stored.
     */
    @XmlTransient
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

    /**
     * @deprecated Use {@link #custom()} with .get("displayOrder", Collections.emptyList()) instead.
     */
    @Override
    @Deprecated
    public List<String> displayOrder() {
        return custom.get("displayOrder", Collections.emptyList());
    }

    @Override
    public String analyzerName() {
        return analyzer;
    }

    /**
     * @deprecated Use {@link #custom()} with .get("unknownValue", "unknown") instead.
     */
    @Override
    @Deprecated
    public String unknownValue() {
        return custom.get("unknownValue", "unknown");
    }

    /**
     * @deprecated Use {@link #custom()} with .get("unknownCondition", "never") instead.
     */
    @Override
    @Deprecated
    public UnknownCondition unknownCondition() {
        return UnknownCondition.fromStringValue(custom.get("unknownCondition", "never"));
    }

    @Override
    public Map<String, Integer> valueDistribution() {
        return values.distribution();
    }

    @Override
    public ValueListComplete isValueListComplete() {
        return values.isComplete();
    }

    /**
     * @deprecated Use {@link #custom()} with .get("displayValues", Collections.emptyMap()) instead.
     */
    @Override
    @Deprecated
    public Map<String, String> displayValues() {
        return custom.get("displayValues", Collections.emptyMap());
    }

    @Override
    public String group() {
        return group;
    }

    /**
     * @deprecated Use {@link #custom()} with .get("uiType", "") instead.
     */
    @Override
    @Deprecated
    public String uiType() {
        return custom.get("uiType", "");
    }
    
    @Override
    public String offsetsField() {
        return name();
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

    /**
     * @deprecated Use {@link #custom()} with .put("unknownValue", ...) instead.
     */
    @Deprecated
    public void setUnknownValue(String unknownValue) {
        if (!this.unknownValue().equals(unknownValue)) {
            ensureNotFrozen();
            custom.put("unknownValue", unknownValue);
        }
    }

    /**
     * @deprecated Use {@link #custom()} with .put("unknownCondition", ...) instead.
     */
    @Deprecated
    public void setUnknownCondition(UnknownCondition unknownCondition) {
        if (!this.unknownCondition().equals(unknownCondition)) {
            ensureNotFrozen();
            this.custom.put("unknownCondition", unknownCondition.stringValue());
        }
    }

    void setValues(JsonNode values) {
        if (this.values.shouldAddValuesWhileIndexing()) {
            ensureNotFrozen();
            this.values.setValues(values);
        }
    }

    /**
     * @deprecated Use {@link #custom()} with .put("displayValue", ...) instead.
     */
    @Deprecated
    public void setDisplayValues(JsonNode displayValues) {
        ensureNotFrozen();
        Map<String, String> map = new HashMap<>();
        Iterator<Entry<String, JsonNode>> it = displayValues.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> entry = it.next();
            String value = entry.getKey();
            String displayValue = entry.getValue().textValue();
            map.put(value, displayValue);
        }
        custom.put("displayValues", map);
    }

    /**
     * @deprecated Use {@link #custom()} with .put("displayValues", ...) instead.
     */
    @Deprecated
    public void setDisplayValues(Map<String, String> displayValues) {
        ensureNotFrozen();
        custom.put("displayValues", new HashMap<>(displayValues));
    }

    /**
     * @deprecated Use {@link #custom()} with .put("displayOrder", ...) instead.
     */
    @Deprecated
    public void setDisplayOrder(List<String> displayOrder) {
        ensureNotFrozen();
        custom.put("displayOrder", new ArrayList<>(displayOrder));
    }

    void setValueListComplete(boolean valueListComplete) {
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

    /**
     * @deprecated Use {@link #custom()} with .put("uiType", ...) instead.
     */
    @Deprecated
    public void setUiType(String uiType) {
        ensureNotFrozen();
        custom.put("uiType", uiType);
    }

    public void setDocValuesType(DocValuesType docValuesType) {
        this.docValuesType = docValuesType;
    }
}
