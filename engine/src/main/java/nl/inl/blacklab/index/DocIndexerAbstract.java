package nl.inl.blacklab.index;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataImpl;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldImpl;
import nl.inl.blacklab.search.indexmetadata.UnknownCondition;

/**
 * Indexes a file.
 */
public abstract class DocIndexerAbstract implements DocIndexer {

    protected static final Logger logger = LogManager.getLogger(DocIndexerAbstract.class);

    private DocWriter docWriter;

    /** Do we want to omit norms? (Default: yes) */
    protected boolean omitNorms = true;

    /**
     * File we're currently parsing. This can be useful for storing the original
     * filename in the index.
     */
    protected String documentName;

    /**
     * The Lucene Document we're currently constructing (corresponds to the document
     * we're indexing)
     */
    protected Document currentLuceneDoc;

    /**
     * Document metadata. Added at the end to deal with unknown values, multiple occurrences
     * (only the first is actually indexed, because of DocValues, among others), etc.
     */
    protected Map<String, List<String>> metadataFieldValues = new HashMap<>();

    /**
     * Parameters passed to this indexer
     */
    protected final Map<String, String> parameters = new HashMap<>();

    final Set<String> numericFields = new HashSet<>();

    /** How many documents we've processed */
    private int numberOfDocsDone = 0;

    /** How many tokens we've processed */
    private int numberOfTokensDone = 0;

    @Override
    public Document getCurrentLuceneDoc() {
        return currentLuceneDoc;
    }

    /**
     * Returns our DocWriter object
     *
     * @return the DocWriter object
     */
    @Override
    public DocWriter getDocWriter() {
        return docWriter;
    }

    /**
     * Set the DocWriter object.
     *
     * We use this to add documents to the index.
     *
     * Called by Indexer when the DocIndexer is instantiated.
     *
     * @param docWriter our DocWriter object
     */
    @Override
    public void setDocWriter(DocWriter docWriter) {
        this.docWriter = docWriter;
    }

    /**
     * Set the file name of the document to index.
     *
     * @param documentName name of the document
     */
    @Override
    public void setDocumentName(String documentName) {
        this.documentName = documentName == null ? "?" : documentName;
    }

    protected org.apache.lucene.document.FieldType luceneTypeFromIndexMetadataType(FieldType type) {
        switch (type) {
        case NUMERIC:
            throw new IllegalArgumentException("Numeric types should be indexed using IntField, etc.");
        case TOKENIZED:
            return getDocWriter().metadataFieldType(true);
        case UNTOKENIZED:
            return getDocWriter().metadataFieldType(false);
        default:
            throw new IllegalArgumentException("Unknown field type: " + type);
        }
    }

    /**
     * Enables or disables norms. Norms are disabled by default.
     *
     * The method name was chosen to match Lucene's Field.setOmitNorms(). Norms are
     * only required if you want to use document-length-normalized scoring.
     *
     * @param b if true, doesn't store norms; if false, does store norms
     */
    @Override
    public void setOmitNorms(boolean b) {
        omitNorms = b;
    }

    @Override
    public void addNumericFields(Collection<String> fields) {
        numericFields.addAll(fields);
    }

    @Override
    public boolean continueIndexing() {
        return getDocWriter().continueIndexing();
    }

    protected void warn(String msg) {
        getDocWriter().listener().warning(msg);
    }

    @Override
    public List<String> getMetadataField(String name) {
        return metadataFieldValues.get(name);
    }

    @Override
    public void addMetadataField(String name, String value) {
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(name))
            logger.warn("Field name '" + name
                    + "' is discouraged (field/annotation names should be valid XML element names)");

        if (name == null || value == null) {
            warn("Incomplete metadata field: " + name + "=" + value + " (skipping)");
            return;
        }

        value = value.trim();
        if (!value.isEmpty()) {
            metadataFieldValues.computeIfAbsent(name, __ -> new ArrayList<>()).add(value);
            IndexMetadataWriter indexMetadata = getDocWriter().indexWriter().metadata();
            indexMetadata.registerMetadataField(name);
        }
    }

    /**
     * Translate a field name before adding it to the Lucene document.
     *
     * By default, simply returns the input. May be overridden to change the name of
     * a metadata field as it is indexed.
     *
     * @param from original metadata field name
     * @return new name
     */
    protected String optTranslateFieldName(String from) {
        return from;
    }

    /**
     * When all metadata values have been set, call this to add the to the Lucene document.
     *
     * We do it this way because we don't want to add multiple values for a field (DocValues and
     * Document.get() only deal with the first value added), and we want to set an "unknown value"
     * in certain conditions, depending on the configuration.
     */
    @Override
    public void addMetadataToDocument() {
        // See what metadatafields are missing or empty and add unknown value if desired.
        IndexMetadataImpl indexMetadata = (IndexMetadataImpl) getDocWriter().indexWriter().metadata();
        Map<String, String> unknownValuesToUse = new HashMap<>();
        List<String> fields = indexMetadata.metadataFields().names();
        for (String field: fields) {
            MetadataField fd = indexMetadata.metadataField(field);
            if (fd.type() == FieldType.NUMERIC)
                continue;
            boolean missing = false, empty = false;
            List<String> currentValue = getMetadataField(fd.name());
            if (currentValue == null)
                missing = true;
            else if (currentValue.isEmpty() || currentValue.stream().allMatch(String::isEmpty))
                empty = true;
            UnknownCondition cond = fd.unknownCondition();
            boolean useUnknownValue = false;
            switch (cond) {
            case EMPTY:
                useUnknownValue = empty;
                break;
            case MISSING:
                useUnknownValue = missing;
                break;
            case MISSING_OR_EMPTY:
                useUnknownValue = missing || empty;
                break;
            case NEVER:
                // (useUnknownValue is already false)
                break;
            }
            if (useUnknownValue) {
                if (empty) {
                    // Don't count this as a value, count the unknown value
                    for (String value: currentValue) {
                        ((MetadataFieldImpl) indexMetadata.metadataFields().get(fd.name())).removeValue(value);
                    }
                }
                unknownValuesToUse.put(optTranslateFieldName(fd.name()), fd.unknownValue());
            }
        }
        for (Entry<String, String> e: unknownValuesToUse.entrySet()) {
            metadataFieldValues.put(e.getKey(), List.of(e.getValue()));
        }
        for (Entry<String, List<String>> e: metadataFieldValues.entrySet()) {
            addMetadataFieldToDocument(e.getKey(), e.getValue());
        }
        metadataFieldValues.clear();
    }

    public void addMetadataFieldToDocument(String name, List<String> values) {
        IndexMetadataWriter indexMetadata = getDocWriter().indexWriter().metadata();
        //indexMetadata.registerMetadataField(name);

        MetadataFieldImpl desc = (MetadataFieldImpl) indexMetadata.metadataFields().get(name);
        FieldType type = desc.type();
        for (String value: values) {
            desc.addValue(value);
        }

        if (type != FieldType.NUMERIC) {
            for (String value: values) {
                currentLuceneDoc.add(new Field(name, value, luceneTypeFromIndexMetadataType(type)));
                // If a value is too long (more than 32K), just truncate it a bit.
                // This should be very rare and would generally only affect sorting/grouping, if anything.
                if (value.length() > MAX_DOCVALUES_LENGTH / 6) { // only when it might be too large...
                    // While it's really too large
                    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
                    while (utf8.length > MAX_DOCVALUES_LENGTH) {
                        // assume all characters take two bytes, truncate and try again
                        int overshoot = utf8.length - MAX_DOCVALUES_LENGTH;
                        int truncateAt = value.length() - 2 * overshoot;
                        if (truncateAt < 1)
                            truncateAt = 1;
                        value = value.substring(0, truncateAt);
                        utf8 = value.getBytes(StandardCharsets.UTF_8);
                    }
                }
                currentLuceneDoc.add(new SortedSetDocValuesField(name,
                        new BytesRef(value))); // docvalues for efficient sorting/grouping
            }
        }
        if (type == FieldType.NUMERIC || numericFields.contains(name)) {
            String numFieldName = name;
            if (type != FieldType.NUMERIC) {
                numFieldName += "Numeric";
            }

            boolean firstValue = true;
            for (String value: values) {
                // Index these fields as numeric too, for faster range queries
                // (we do both because fields sometimes aren't exclusively numeric)
                int n;
                try {
                    n = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // This just happens sometimes, e.g. given multiple years, or
                    // descriptive text like "around 1900". OK to ignore.
                    n = 0;
                }
                IntPoint nf = new IntPoint(numFieldName, n);
                currentLuceneDoc.add(nf);
                currentLuceneDoc.add(new StoredField(numFieldName, n));
                if (firstValue)
                    currentLuceneDoc.add(
                            new NumericDocValuesField(numFieldName, n)); // docvalues for efficient sorting/grouping
                else {
                    warn(documentName + " contains multiple values for single-valued numeric field " + numFieldName
                            + "(values: " + StringUtils.join(values, "; ") + ")");
                }
                firstValue = false;
            }
        }
    }

    /**
     * Add the field, with all its properties, to the forward index.
     *
     * @param field field to add to the forward index
     */
    protected void addToForwardIndex(AnnotatedFieldWriter field) {
        getDocWriter().addToForwardIndex(field, currentLuceneDoc);
    }

    protected abstract int getCharacterPosition();

    /**
     * Keep track of how many tokens have been processed.
     */
    @Override
    public void documentDone(String documentName) {
        numberOfDocsDone++;
        getDocWriter().listener().documentDone(documentName);
    }

    /**
     * Keep track of how many tokens have been processed.
     */
    @Override
    public void tokensDone(int n) {
        numberOfTokensDone += n;
        getDocWriter().listener().tokensDone(n);
    }

    @Override
    public int numberOfDocsDone() {
        return numberOfDocsDone;
    }

    @Override
    public long numberOfTokensDone() {
        return numberOfTokensDone;
    }

}
