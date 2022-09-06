package nl.inl.blacklab.index.annotated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.analysis.AddIsPrimaryValueToPayloadFilter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.CollUtil;

/**
 * An annotation in an annotated field (while indexing). See AnnotatedFieldWriter for details.
 */
public class AnnotationWriter {

    /** Maximum length a value is allowed to be. */
    private static final int MAXIMUM_VALUE_LENGTH = 1000;

    private final AnnotatedFieldWriter fieldWriter;

    private final AnnotationSensitivities sensitivitySetting;

    protected boolean includeOffsets;

    /**
     * Term values for this annotation.
     */
    protected List<String> values = new ArrayList<>();

    /**
     * Token position increments. This allows us to index multiple terms at a single
     * token position (just set the token increments of the additional tokens to 0).
     */
    protected IntArrayList increments = new IntArrayList();

    /**
     * Payloads for this annotation, if any.
     */
    protected List<BytesRef> payloads = null;

    /**
     * Position of the last value added
     */
    protected int lastValuePosition = -1;

    /**
     * A annotation may be indexed in different ways (sensitivities). This specifies
     * names and filters for each way.
     */
    private final Map<String, TokenFilterAdder> sensitivities = new HashMap<>();

    /** The main sensitivity (the one that gets character offsets if desired) */
    private String mainSensitivity;

    /** The annotation name */
    private final String annotationName;

    /** The annotation descriptor */
    private Annotation annotation;

    /** Does this annotation get its own forward index? */
    private boolean hasForwardIndex = true;

    /** Should the payload indicate whether this token is primary or secondary? (see PayloadUtils) */
    private boolean needsPrimaryValuePayload = false;

    public String mainSensitivity() {
        return mainSensitivity;
    }

    public void setAnnotation(Annotation annotation) {
        this.annotation = annotation;
    }

    public Annotation annotation() {
        return annotation;
    }

    public boolean includeOffsets() {
        return includeOffsets;
    }

    /**
     * Construct a AnnotationWriter object
     *
     * @param fieldWriter fieldwriter for our field
     * @param name annotation name
     * @param sensitivity ways to index this annotation, with respect to case- and
     *            diacritics-sensitivity.
     * @param includeOffsets whether to include character offsets in the main
     *            sensitivity variant
     * @param includePayloads will this annotation include payloads?
     * @param needsPrimaryValuePayloads should payloads indicate whether this is a primary value? (forces payloads)
     */
    public AnnotationWriter(AnnotatedFieldWriter fieldWriter, String name, AnnotationSensitivities sensitivity,
            boolean includeOffsets, boolean includePayloads, boolean needsPrimaryValuePayloads) {
        super();
        this.fieldWriter = fieldWriter;
        annotationName = name;
        this.sensitivitySetting = sensitivity;
        if (fieldWriter.field() != null) {
            annotation = fieldWriter.field().annotation(annotationName);
        }

        mainSensitivity = null;
        if (sensitivity != AnnotationSensitivities.ONLY_INSENSITIVE) {
            // Add sensitive sensitivity
            mainSensitivity = MatchSensitivity.SENSITIVE.luceneFieldSuffix();
            sensitivities.put(mainSensitivity, null);
        }
        if (sensitivity != AnnotationSensitivities.ONLY_SENSITIVE) {
            // Add insensitive sensitivity
            sensitivities.put(MatchSensitivity.INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(true, true));
            if (mainSensitivity == null)
                mainSensitivity = MatchSensitivity.INSENSITIVE.luceneFieldSuffix();
        }
        if (sensitivity == AnnotationSensitivities.CASE_AND_DIACRITICS_SEPARATE) {
            // Add case-insensitive and diacritics-insensitive sensitivity
            sensitivities.put(MatchSensitivity.CASE_INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(true, false));
            sensitivities.put(MatchSensitivity.DIACRITICS_INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(false, true));
        }

        this.includeOffsets = includeOffsets;
        this.needsPrimaryValuePayload = needsPrimaryValuePayloads;
        if (!includePayloads && needsPrimaryValuePayloads)
            includePayloads = true;
        if (includePayloads)
            payloads = new ArrayList<>();
    }

    public Collection<String> sensitivitySuffixes() {
        return Collections.unmodifiableCollection(sensitivities.keySet());
    }

    TokenStream tokenStream(String sensitivityName, IntArrayList startChars, IntArrayList endChars) {
        TokenStream ts;
        if (includeOffsets) {
            ts = new TokenStreamWithOffsets(values, increments, startChars, endChars);
        } else {
            ts = new TokenStreamFromList(values, increments, payloads);
        }
        TokenFilterAdder filterAdder = sensitivities.get(sensitivityName);
        if (filterAdder != null)
            return filterAdder.addFilters(ts);

        if (hasForwardIndex && needsPrimaryValuePayload) {
            // When writing the segment, we'll need to know which of our values was our "primary"
            // value (the original word, to be used in concordances, sort, group, etc., to be stored
            // in the forward index) and which were the secondary ones (e.g. stemmed, synonyms).
            // This information is encoded into the payloads. When using payloads for an annotation that
            // has these indicator, you should check if the indicator is there and skip it (see PayloadUtils).
            ts = new AddIsPrimaryValueToPayloadFilter(ts);
        }

        return ts;
    }

    FieldType getFieldType(String sensitivityName) {
        boolean isMainAnnotation = fieldWriter.mainAnnotation() == this;
        boolean isMainSensitivity = sensitivityName.equals(mainSensitivity);

        // Main sensitivity of main annotation gets character offsets
        // (if it's the main annotation of an annotated field)
        boolean offsets = includeOffsets && isMainSensitivity;

        // Main sensitivity of main annotation may get content store
        boolean contentStore = false; // @@@ WIP  isMainAnnotation && isMainSensitivity && field().hasContentStore();
        return BLAnnotFieldTypes.get(offsets, hasForwardIndex, contentStore);
    }

    public void addToLuceneDoc(Document doc, String annotatedFieldName, IntArrayList startChars,
            IntArrayList endChars) {
        for (String sensitivityName : sensitivities.keySet()) {
            FieldType fieldType = getFieldType(sensitivityName);
            TokenStream tokenStream = tokenStream(sensitivityName, startChars, endChars);
            String luceneFieldName = AnnotatedFieldNameUtil.annotationField(annotatedFieldName,
                    annotationName, sensitivityName);
            doc.add(new Field(luceneFieldName, tokenStream, fieldType));
        }
    }

    public List<String> values() {
        return Collections.unmodifiableList(values);
    }

    public List<Integer> positionIncrements() {
        return CollUtil.toJavaList(increments);
    }

    public int lastValuePosition() {
        return lastValuePosition;
    }

    public String name() {
        return annotationName;
    }

    public boolean hasForwardIndex() {
        return hasForwardIndex;
    }

    public void setHasForwardIndex(boolean b) {
        hasForwardIndex = b;
    }

    /**
     * Add a value to the annotation.
     *
     * @param value value to add
     */
    final public void addValue(String value) {
        addValue(value, 1);
    }

    /**
     * Add a value to the annotation.
     *
     * @param value the value to add
     * @param increment number of tokens distance from the last token added
     */
    public void addValue(String value, int increment) {
        if (value.length() > MAXIMUM_VALUE_LENGTH) {
            // Let's keep a sane maximum value length.
            // (Lucene's is 32766, but we don't want to go that far)
            value = value.substring(0, MAXIMUM_VALUE_LENGTH);
        }

        // Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
        value = value.intern();

        // Special case: if previous value was the empty string and position increment is 0,
        // replace the previous value. This is convenient to keep all the annotations synched
        // up while indexing (by adding an empty string if we don't have a value for a
        // annotation), while still being able to add a value to this position later (for example,
        // when we encounter an XML close tag.
        int lastIndex = values.size() - 1;
        if (lastIndex >= 0 && values.get(lastIndex).length() == 0) {
            // Change the last value and its position increment
            values.set(lastIndex, value);
            if (increment > 0)
                increments.set(lastIndex, increments.get(lastIndex) + increment);
            lastValuePosition += increment; // keep track of position of last token
            return;
        }

        values.add(value);
        increments.add(increment);
        lastValuePosition += increment; // keep track of position of last token
    }

    /**
     * Add a value to the annotation at a specific position.
     *
     * Please note that if you add a value beyond the current position, the next
     * call to addValue() will add from this new position! This is not an issue if
     * you add a value at a lower position (that operation doesn't change the
     * current last token position used for addValue()).
     *
     * @param value the value to add
     * @param position the position to put it at
     * @return new position of the last token, in case it changed.
     */
    public int addValueAtPosition(String value, int position) {
        if (value.length() > MAXIMUM_VALUE_LENGTH) {
            // Let's keep a sane maximum value length.
            // (Lucene's is 32766, but we don't want to go that far)
            value = value.substring(0, MAXIMUM_VALUE_LENGTH);
        }

        // Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
        value = value.intern();

        if (position >= lastValuePosition) {
            // Beyond the last position; regular addValue()
            addValue(value, position - lastValuePosition);
        } else {
            // Before the last position.
            // Find the index where the value should be inserted.
            int curPos = this.lastValuePosition;
            for (int i = values.size() - 1; i >= 0; i--) {
                if (curPos <= position) {
                    // Value should be inserted after this index.
                    int n = i + 1;
                    insertValueAtPosition(value, position, n, curPos);
                    break;
                }
                curPos -= increments.get(i); // go to previous value position
            }
            if (curPos == -1) {
                // Value should be inserted at the first position.
                int n = 0;
                insertValueAtPosition(value, position, n, curPos);
            }
        }

        return lastValuePosition;
    }

    /**
     * Add a value at a specific token position.
     *
     * @param value value to add
     * @param valuePosition token position of this value
     * @param index index in the arrays where this value goes
     * @param previousPosition token position of the previous value in the arrays (to calculate positionIncrement)
     */
    private void insertValueAtPosition(String value, int valuePosition, int index, int previousPosition) {
        values.add(index, value);
        int positionIncrement = valuePosition - previousPosition;
        increments.addAtIndex(index, positionIncrement);
        if (increments.size() > index + 1 && positionIncrement > 0) {
            // Inserted value wasn't the last value, so the
            // increment for the value after this is now wrong;
            // correct it.
            increments.set(index + 1, increments.get(index + 1) - positionIncrement);
        }
    }

    public void addPayload(BytesRef payload) {
        payloads.add(payload);
    }

    public int lastValueIndex() {
        return values.size() - 1;
    }

    public void setPayloadAtIndex(int i, BytesRef payload) {
        payloads.set(i, payload);
    }

    public void clear() {
        lastValuePosition = -1;
        // Don't reuse buffers, reclaim memory so we don't run out
        values = new ArrayList<>();
        increments = new IntArrayList();
        if (payloads != null) {
            payloads = new ArrayList<>();
        }
    }

    public boolean hasPayload() {
        return payloads != null;
    }

    public AnnotatedField field() {
        return fieldWriter.field();
    }

    @Override
    public String toString() {
        return "AnnotationWriter(" + field() + "." + annotationName + ")";
    }

    public AnnotationSensitivities getSensitivitySetting() {
        return sensitivitySetting;
    }
}
