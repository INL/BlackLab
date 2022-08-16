package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import nl.inl.blacklab.indexers.config.ConfigMetadataField;

/**
 * The metadata fields in an index.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({ "defaultAnalyzer", "pidField", "throwOnMissingField", "fields" })
class MetadataFieldsImpl implements MetadataFieldsWriter, Freezable<MetadataFieldsImpl> {

    private static final Logger logger = LogManager.getLogger(MetadataFieldsImpl.class);

    /**
     * Logical groups of metadata fields, for presenting them in the user interface.
     */
    @XmlTransient
    private final Map<String, MetadataFieldGroupImpl> metadataGroups = new LinkedHashMap<>();

    /** All non-annotated fields in our index (metadata fields) and their types. */
    @JsonProperty("fields")
    private final Map<String, MetadataFieldImpl> metadataFieldInfos;

    /** What MetadataFieldValues implementation to use (store in indexmetadata or get from index) */
    @XmlTransient
    private MetadataFieldValues.Factory metadataFieldValuesFactory;

    /**
     * When a metadata field value is considered "unknown"
     * (never|missing|empty|missing_or_empty) [never]
     */
    @XmlTransient
    private String defaultUnknownCondition = "never";

    /** What value to index when a metadata field value is unknown [unknown] */
    @XmlTransient
    private String defaultUnknownValue = "unknown";

    /** Top-level custom props (if using integrated input format), for special fields, etc. */
    @XmlTransient
    private CustomPropsMap topLevelCustom;

    /** Metadata field containing document title */
    @XmlTransient
    private String titleField;

    /** Metadata field containing document author */
    @XmlTransient
    private String authorField;

    /** Metadata field containing document date */
    @XmlTransient
    private String dateField;

    /** Metadata field containing document pid */
    private String pidField;

    /** Default analyzer to use for metadata fields */
    private String defaultAnalyzer = "DEFAULT";

    /** Is the object frozen, not allowing any modifications? */
    @XmlTransient
    private boolean frozen = false;

    /** If we try to get() a missing field, should we throw or return a default config?
     *  Should eventually be eliminated when we can enforce all metadatafields to be declared.
     */
    private boolean throwOnMissingField = true;

    /** If throwOnMissingField is false, the implicit field configs are stored here.
     *  This map may be modified even if this instance is frozen.
     *  Should eventually be eliminated when we can enforce all metadatafields to be declared.
     */
    @XmlTransient
    private Map<String, MetadataField> implicitFields = new ConcurrentHashMap<>();

    void addFromConfig(ConfigMetadataField f) {
        put(f.getName(), MetadataFieldImpl.fromConfig(f, this));
    }

    public MetadataFieldValues.Factory getMetadataFieldValuesFactory() {
        return metadataFieldValuesFactory;
    }

    // For JAXB deserialization
    @SuppressWarnings("unused")
    MetadataFieldsImpl() {
        metadataFieldInfos = new TreeMap<>();
    }

    MetadataFieldsImpl(MetadataFieldValues.Factory metadataFieldValuesFactory) {
        this.metadataFieldValuesFactory = metadataFieldValuesFactory;
        metadataFieldInfos = new TreeMap<>();
    }

    public void setThrowOnMissingField(boolean throwOnMissingField) {
        this.throwOnMissingField = throwOnMissingField;
    }

    @Override
    public String defaultAnalyzerName() {
        return defaultAnalyzer;
    }

    @Override
    public Iterator<MetadataField> iterator() {
        final Iterator<MetadataFieldImpl> it = metadataFieldInfos.values().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public MetadataField next() {
                return it.next();
            }
        };
    }

    @Override
    public Stream<MetadataField> stream() {
        return metadataFieldInfos.values().stream().map(f -> f);
    }

    @Override
    public MetadataField get(String fieldName) {
        MetadataField d = null;
        // Synchronized because we sometimes register new metadata fields during indexing
        synchronized (metadataFieldInfos) {
            d = metadataFieldInfos.get(fieldName);
        }
        if (d == null) {
            if (!throwOnMissingField) {
                // Don't throw an exception like we used to do; instead return a default tokenized field.
                // This allows us to handle the situation where not all metadata fields were registered
                // before indexing, and unregistered tokenized metadata fields were added during indexing.
                // (metadata can't change while indexing for integrated index)
                return registerImplicit(fieldName);
            } else {
                // Old behaviour: just throw an exception
                throw new IllegalArgumentException("Metadata field '" + fieldName + "' not found!");
            }
        }
        return d;
    }

    private MetadataField registerImplicit(String fieldName) {
        return implicitFields.computeIfAbsent(fieldName,
                __ -> {
                    logger.warn("Encountered undeclared metadata field '" + fieldName + "'. Make sure all metadata fields are declared.");
                    return new MetadataFieldImpl(fieldName, FieldType.TOKENIZED, metadataFieldValuesFactory);
                }
        );
    }

    @Override
    public boolean exists(String name) {
        return metadataFieldInfos.containsKey(name);
    }

    @Override
    public MetadataFieldGroups groups() {
        return new MetadataFieldGroups() {
            @Override
            public Iterator<MetadataFieldGroup> iterator() {
                Iterator<MetadataFieldGroupImpl> it = metadataGroups.values().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public MetadataFieldGroup next() {
                        return it.next();
                    }
                };
            }

            @Override
            public Stream<MetadataFieldGroup> stream() {
                return metadataGroups.values().stream().map(g -> g);
            }

            @Override
            public MetadataFieldGroup get(String name) {
                return metadataGroups.get(name);
            }
        };
    }

    @Override
    public MetadataField special(String specialFieldType) {
        switch(specialFieldType) {
        case "pid":
            return pidField != null && metadataFieldInfos.containsKey(pidField) ? get(pidField) : null;
        case "title":
            return titleField != null && metadataFieldInfos.containsKey(titleField) ? get(titleField) : null;
        case "author":
            return authorField != null && metadataFieldInfos.containsKey(authorField) ? get(authorField) : null;
        case "date":
            return dateField != null && metadataFieldInfos.containsKey(dateField) ? get(dateField) : null;
        }
        return null;
    }

    public MetadataField titleField() {
        return special(TITLE);
    }

    public MetadataField authorField() {
        return special(AUTHOR);
    }

    public MetadataField pidField() {
        return special(PID);
    }

    public MetadataField dateField() {
        return special(DATE);
    }

    /**
     * Find the first (alphabetically) field whose name matches (case-insensitively) the search string.
     *
     * @param search the string to search for
     * @return the field name, or null if no fields matched
     */
    MetadataField findTextField(String search) {
        // Find documents with title in the name
        List<MetadataField> fieldsFound = new ArrayList<>();
        for (MetadataField field: metadataFieldInfos.values()) {
            if (field.type() == FieldType.TOKENIZED && field.name().equalsIgnoreCase(search))
                fieldsFound.add(field);
        }
        if (fieldsFound.isEmpty())
            return null;

        // Sort (so we always return the same field if more than one matches
        fieldsFound.sort(Comparator.comparing(Field::name));
        return fieldsFound.get(0);
    }

    public String defaultUnknownCondition() {
        return defaultUnknownCondition;
    }

    public String defaultUnknownValue() {
        return defaultUnknownValue;
    }

    // Methods that mutate data
    // ------------------------------------

    @Override
    public synchronized MetadataField register(String fieldName) {
        if (fieldName == null)
            throw new IllegalArgumentException("Tried to register a metadata field with null as name");
        // Synchronized because we might be using the map in another indexing thread
        synchronized (metadataFieldInfos) {
            MetadataFieldImpl mf;
            if (metadataFieldInfos.containsKey(fieldName))
                mf = metadataFieldInfos.get(fieldName);
            else {
                if (isFrozen() && !throwOnMissingField) {
                    // Metadata is frozen. Instead of really registering metadata
                    // field, we'll register an "implicit field". This is a metadata
                    // field whose configuration should match the default.
                    // With throwOnMissingField set to false, get() will also return a
                    // default config for missing fields.
                    return registerImplicit(fieldName);
                }
                // Not registered yet; do so now.
                ensureNotFrozen();
                FieldType fieldType = FieldType.TOKENIZED;
                if (fieldName.equals("fromInputFile")) {
                    // internal bookkeeping field, never tokenize this
                    // (probably better to register this field properly, but this works for now)
                    fieldType = FieldType.UNTOKENIZED;
                }
                mf = new MetadataFieldImpl(fieldName, fieldType, metadataFieldValuesFactory);
                mf.custom().put("unknownCondition", defaultUnknownCondition());
                mf.custom().put("unknownValue", defaultUnknownValue());
                metadataFieldInfos.put(fieldName, mf);
            }
            return mf;
        }
    }

    @Override
    public void setMetadataGroups(Map<String, MetadataFieldGroupImpl> metadataGroups) {
        if (this.metadataGroups == null || !this.metadataGroups.equals(metadataGroups)) {
            ensureNotFrozen();
            this.metadataGroups.clear();
            this.metadataGroups.putAll(metadataGroups);
            if (topLevelCustom != null)
                topLevelCustom.put("metadataFieldGroups", metadataGroups);
        }
    }

    @Override
    public synchronized void put(String fieldName, MetadataFieldImpl fieldDesc) {
        ensureNotFrozen();
        metadataFieldInfos.put(fieldName, fieldDesc);
    }

    @Override
    public void resetForIndexing() {
        ensureNotFrozen();
        for (MetadataFieldImpl f: metadataFieldInfos.values()) {
            f.resetForIndexing();
        }
    }

    @Override
    public void setDefaultUnknownCondition(String unknownCondition) {
        ensureNotFrozen();
        this.defaultUnknownCondition = unknownCondition;
    }

    @Override
    public void setDefaultUnknownValue(String value) {
        ensureNotFrozen();
        this.defaultUnknownValue = value;
    }

    @Override
    public void clearSpecialFields() {
        ensureNotFrozen();
        titleField = authorField = dateField = pidField = null;
    }

    @Override
    public void setSpecialField(String specialFieldType, String fieldName) {
        ensureNotFrozen();
        switch(specialFieldType) {
        case MetadataFields.PID:
            pidField = fieldName;
            break;
        case MetadataFields.TITLE:
            titleField = fieldName;
            break;
        case MetadataFields.AUTHOR:
            authorField = fieldName;
            break;
        case MetadataFields.DATE:
            dateField = fieldName;
            break;
        default:
            throw new IllegalArgumentException("Unknown special field type: " + fieldName);
        }
        // Also update top-level custom properties, where these special fields (except pidField)
        // are stored (in the integrated input format)
        if (topLevelCustom != null && !specialFieldType.equals(MetadataFields.PID))
            topLevelCustom.put(specialFieldType + "Field", fieldName);
    }

    @Override
    public void setDefaultAnalyzer(String name) {
        ensureNotFrozen();
        this.defaultAnalyzer = name;
    }

    @Override
    public void freeze() {
        this.frozen = true;
        for (MetadataFieldImpl field: metadataFieldInfos.values()) {
            field.freeze();
        }
    }

    @Override
    public synchronized boolean isFrozen() {
        return this.frozen;
    }

    @Override
    public String toString() {
        return metadataFieldInfos.keySet().toString();
    }

    @Override
    public List<String> names() {
        return new ArrayList<>(metadataFieldInfos.keySet());
    }

    public void fixAfterDeserialization(IndexMetadataIntegrated metadata, MetadataFieldValues.Factory factory) {
        setTopLevelCustom(metadata.custom());

        metadataFieldValuesFactory = factory;
        for (Map.Entry<String, MetadataFieldImpl> e: metadataFieldInfos.entrySet()) {
            e.getValue().fixAfterDeserialization(metadata.index, e.getKey(), factory);
        }

        // Get our special fields from the top-level custom props
        if (topLevelCustom.containsKey("authorField"))
            setSpecialField(MetadataFields.AUTHOR, (String) topLevelCustom.get("authorField"));
        if (topLevelCustom.containsKey("dateField"))
            setSpecialField(MetadataFields.DATE, (String) topLevelCustom.get("dateField"));
        if (topLevelCustom.containsKey("titleField"))
            setSpecialField(MetadataFields.TITLE, (String) topLevelCustom.get("titleField"));

        if (topLevelCustom.containsKey("metadataFieldGroups")) {
            metadataGroups.clear();
            metadataGroups.putAll((Map<String, MetadataFieldGroupImpl>) topLevelCustom.get("metadataFieldGroups"));
            metadataGroups.forEach((k, v) -> v.fixAfterDeserialization(this));
        }
    }

    public void setTopLevelCustom(CustomPropsMap topLevelCustom) {
        this.topLevelCustom = topLevelCustom;
    }

}
