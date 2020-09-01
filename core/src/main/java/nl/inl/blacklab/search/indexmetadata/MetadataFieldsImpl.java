package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The metadata fields in an index.
 */
class MetadataFieldsImpl implements MetadataFieldsWriter, Freezable<MetadataFieldsImpl> {

    /**
     * Logical groups of metadata fields, for presenting them in the user interface.
     */
    private Map<String, MetadataFieldGroupImpl> metadataGroups = new LinkedHashMap<>();

    /** All non-annotated fields in our index (metadata fields) and their types. */
    private Map<String, MetadataFieldImpl> metadataFieldInfos;

    /**
     * When a metadata field value is considered "unknown"
     * (never|missing|empty|missing_or_empty) [never]
     */
    private String defaultUnknownCondition;

    /** What value to index when a metadata field value is unknown [unknown] */
    private String defaultUnknownValue;

    /** Metadata field containing document title */
    private String titleField;

    /** Metadata field containing document author */
    private String authorField;

    /** Metadata field containing document date */
    private String dateField;

    /** Metadata field containing document pid */
    private String pidField;

    /** Default analyzer to use for metadata fields */
    private String defaultAnalyzerName;

    /** Is the object frozen, not allowing any modifications? */
    private boolean frozen = false;

    MetadataFieldsImpl() {
        metadataFieldInfos = new TreeMap<>();
    }

    @Override
    public String defaultAnalyzerName() {
        return defaultAnalyzerName;
    }

    @Override
    public Iterator<MetadataField> iterator() {
        final Iterator<MetadataFieldImpl> it = metadataFieldInfos.values().iterator();
        return new Iterator<MetadataField>() {
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
        return metadataFieldInfos.values().stream().map(f -> (MetadataField)f);
    }

    @Override
    public MetadataField get(String fieldName) {
        MetadataField d = null;
        // Synchronized because we sometimes register new metadata fields during indexing
        synchronized (metadataFieldInfos) {
            d = metadataFieldInfos.get(fieldName);
        }
        if (d == null)
            throw new IllegalArgumentException("Metadata field '" + fieldName + "' not found!");
        return d;
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
                return new Iterator<MetadataFieldGroup>() {
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
                return metadataGroups.values().stream().map(g -> (MetadataFieldGroup)g);
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
        fieldsFound.sort( (a, b) -> a.name().compareTo(b.name()) );
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
        ensureNotFrozen();
        if (fieldName == null)
            throw new IllegalArgumentException("Tried to register a metadata field with null as name");
        // Synchronized because we might be using the map in another indexing thread
        synchronized (metadataFieldInfos) {
            MetadataFieldImpl mf;
            if (metadataFieldInfos.containsKey(fieldName))
                mf = metadataFieldInfos.get(fieldName);
            else {
                // Not registered yet; do so now.
                FieldType fieldType = FieldType.TOKENIZED;
                if (fieldName.equals("fromInputFile")) {
                    // internal bookkeeping field, never tokenize this
                    // (probably better to register this field properly, but this works for now)
                    fieldType = FieldType.UNTOKENIZED;
                }
                mf = new MetadataFieldImpl(fieldName, fieldType);
                mf.setUnknownCondition(UnknownCondition.fromStringValue(defaultUnknownCondition));
                mf.setUnknownValue(defaultUnknownValue);
                metadataFieldInfos.put(fieldName, mf);
            }
            return mf;
        }
    }

    @Override
    public void clearMetadataGroups() {
        ensureNotFrozen();
        metadataGroups.clear();
    }

    @Override
    public void putMetadataGroup(String name, MetadataFieldGroupImpl metadataGroup) {
        ensureNotFrozen();
        metadataGroups.put(name, metadataGroup);
    }

    /**
     * Check if field exists, or create a default (tokenized) field for it if not.
     *
     * @param name
     */
    @Override
    public void ensureFieldExists(String name) {
        if (!exists(name)) {
            ensureNotFrozen();
            MetadataFieldImpl mf = new MetadataFieldImpl(name, FieldType.TOKENIZED);
            mf.setUnknownCondition(UnknownCondition.fromStringValue(defaultUnknownCondition));
            mf.setUnknownValue(defaultUnknownValue);
            metadataFieldInfos.put(name, mf);
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
        case "pid":
            pidField = fieldName;
            break;
        case "title":
            titleField = fieldName;
            break;
        case "author":
            authorField = fieldName;
            break;
        case "date":
            dateField = fieldName;
            break;
        default:
            throw new IllegalArgumentException("Unknown special field type: " + fieldName);
        }
    }

    @Override
    public void setDefaultAnalyzerName(String name) {
        ensureNotFrozen();
        this.defaultAnalyzerName = name;
    }

    @Override
    public MetadataFieldsImpl freeze() {
        this.frozen = true;
        for (MetadataFieldImpl field: metadataFieldInfos.values()) {
            field.freeze();
        }
        return this;
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

}