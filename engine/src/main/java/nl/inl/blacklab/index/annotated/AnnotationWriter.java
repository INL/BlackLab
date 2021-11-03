/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
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
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

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

    /** The field type for annotations without character offsets */
    private static FieldType tokenStreamFieldNoOffsets;

    /**
     * The field type for annotations with character offsets (on the main sensitivity variant)
     */
    private static FieldType tokenStreamFieldWithOffsets;

    static {
        FieldType type = tokenStreamFieldNoOffsets = new FieldType();
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        type.setTokenized(true);
        type.setOmitNorms(true);
        type.setStored(false);
        type.setStoreTermVectors(true);
        type.setStoreTermVectorPositions(true);
        type.setStoreTermVectorOffsets(false);
        type.freeze();

        type = tokenStreamFieldWithOffsets = new FieldType(tokenStreamFieldNoOffsets);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        type.setStoreTermVectorOffsets(true);
        type.freeze();
    }

    /**
     * How an annotation is to be indexed with respect to case and diacritics
     * sensitivity.
     */
    public enum SensitivitySetting {
        DEFAULT, // "choose default based on field name"
        ONLY_SENSITIVE, // only index case- and diacritics-sensitively
        ONLY_INSENSITIVE, // only index case- and diacritics-insensitively
        SENSITIVE_AND_INSENSITIVE, // case+diac sensitive as well as case+diac insensitive
        CASE_AND_DIACRITICS_SEPARATE; // all four combinations (sens, insens, case-insens, diac-insens)

        public static SensitivitySetting fromStringValue(String v) {
            switch (v.toLowerCase()) {
            case "default":
            case "":
                return DEFAULT;
            case "sensitive":
            case "s":
                return ONLY_SENSITIVE;
            case "insensitive":
            case "i":
                return ONLY_INSENSITIVE;
            case "sensitive_insensitive":
            case "si":
                return SENSITIVE_AND_INSENSITIVE;
            case "case_diacritics_separate":
            case "all":
                return CASE_AND_DIACRITICS_SEPARATE;
            default:
                throw new IllegalArgumentException("Unknown string value for SensitivitySetting: " + v
                        + " (should be default|sensitive|insensitive|sensitive_insensitive|case_diacritics_separate or s|i|si|all)");
            }
        }
    }

    private AnnotatedFieldWriter fieldWriter;

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
    private Map<String, TokenFilterAdder> sensitivities = new HashMap<>();

    /** The main sensitivity (the one that gets character offsets if desired) */
    private String mainSensitivity;

    /** The annotation name */
    private String annotationName;

    /** The annotation descriptor */
    private Annotation annotation;

    /** Does this annotation get its own forward index? */
    private boolean hasForwardIndex = true;

    /**
     * To keep memory usage down, we make sure we only store 1 copy of each string
     * value
     */
    private Map<String, String> storedValues = new HashMap<>();

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
     */
    public AnnotationWriter(AnnotatedFieldWriter fieldWriter, String name, SensitivitySetting sensitivity,
            boolean includeOffsets, boolean includePayloads) {
        super();
        this.fieldWriter = fieldWriter;
        annotationName = name;
        if (fieldWriter.field() != null) {
            annotation = fieldWriter.field().annotation(annotationName);
        }

        mainSensitivity = null;
        if (sensitivity != SensitivitySetting.ONLY_INSENSITIVE) {
            // Add sensitive sensitivity
            mainSensitivity = MatchSensitivity.SENSITIVE.luceneFieldSuffix();
            sensitivities.put(mainSensitivity, null);
        }
        if (sensitivity != SensitivitySetting.ONLY_SENSITIVE) {
            // Add insensitive sensitivity
            sensitivities.put(MatchSensitivity.INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(true, true));
            if (mainSensitivity == null)
                mainSensitivity = MatchSensitivity.INSENSITIVE.luceneFieldSuffix();
        }
        if (sensitivity == SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE) {
            // Add case-insensitive and diacritics-insensitive sensitivity
            sensitivities.put(MatchSensitivity.CASE_INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(true, false));
            sensitivities.put(MatchSensitivity.DIACRITICS_INSENSITIVE.luceneFieldSuffix(), new DesensitizerAdder(false, true));
        }

        this.includeOffsets = includeOffsets;
        if (includePayloads)
            payloads = new ArrayList<>();
    }

    public Collection<String> sensitivitySuffixes() {
        return Collections.unmodifiableCollection(sensitivities.keySet());
    }

    TokenStream tokenStream(String altName, IntArrayList startChars, IntArrayList endChars) {
        TokenStream ts;
        if (includeOffsets) {
            ts = new TokenStreamWithOffsets(values, increments, startChars, endChars);
        } else {
            ts = new TokenStreamFromList(values, increments, payloads);
        }
        TokenFilterAdder filterAdder = sensitivities.get(altName);
        if (filterAdder != null)
            return filterAdder.addFilters(ts);
        return ts;
    }

    FieldType termVectorOptionFieldType(String altName) {
        // Main sensitivity of a annotation may get character offsets
        // (if it's the main annotation of an annotated field)
        if (includeOffsets && altName.equals(mainSensitivity))
            return tokenStreamFieldWithOffsets;

        // Named sensitivities and additional annotations don't get character offsets
        return tokenStreamFieldNoOffsets;
    }

    public void addToLuceneDoc(Document doc, String fieldName, IntArrayList startChars,
            IntArrayList endChars) {
        for (String altName : sensitivities.keySet()) {
            doc.add(new Field(AnnotatedFieldNameUtil.annotationField(fieldName, annotationName, altName),
                    tokenStream(altName, startChars, endChars), termVectorOptionFieldType(altName)));
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
        String storedValue = storedValues.get(value);
        if (storedValue == null) {
            storedValues.put(value, value);
            storedValue = value;
        }

        // Special case: if previous value was the empty string and position increment is 0,
        // replace the previous value. This is convenient to keep all the annotations synched
        // up while indexing (by adding an empty string if we don't have a value for a
        // annotation), while still being able to add a value to this position later (for example,
        // when we encounter an XML close tag.
        int lastIndex = values.size() - 1;
        if (lastIndex >= 0 && values.get(lastIndex).length() == 0) {
            // Change the last value and its position increment
            values.set(lastIndex, storedValue);
            if (increment > 0)
                increments.set(lastIndex, increments.get(lastIndex) + increment);
            lastValuePosition += increment; // keep track of position of last token
            return;
        }

        values.add(storedValue);
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

        if (position >= lastValuePosition) {
            // Beyond the last position; regular addValue()
            addValue(value, position - lastValuePosition);
        } else {
            // Make sure we don't keep duplicates of strings in memory, but re-use earlier instances.
            String storedValue = storedValues.get(value);
            if (storedValue == null) {
                storedValues.put(value, value);
                storedValue = value;
            }

            // Before the last position.
            // Find the index where the value should be inserted.
            int curPos = this.lastValuePosition;
            for (int i = values.size() - 1; i >= 0; i--) {
                if (curPos <= position) {
                    // Value should be inserted after this index.
                    int n = i + 1;
                    values.add(n, storedValue);
                    int incr = position - curPos;
                    increments.addAtIndex(n, incr);
                    if (increments.size() > n + 1 && incr > 0) {
                        // Inserted value wasn't the last value, so the
                        // increment for the value after this is now wrong;
                        // correct it.
                        increments.set(n + 1, increments.get(n + 1) - incr);
                    }
                    break;
                }
                curPos -= increments.get(i); // go to previous value position
            }
            if (curPos == -1) {
                // Value should be inserted at the first position.
                int n = 0;
                values.add(n, storedValue);
                int incr = position - curPos;
                increments.addAtIndex(n, incr);
                if (increments.size() > n + 1 && incr > 0) {
                    // Inserted value wasn't the last value, so the
                    // increment for the value after this is now wrong;
                    // correct it.
                    increments.set(n + 1, increments.get(n + 1) - incr);
                }
            }
        }

        return lastValuePosition;
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

    public void clear(boolean reuseBuffers) {
        lastValuePosition = -1;
        // In theory, we don't need to clear the cached values between documents, but
        // for large data sets, this would keep getting larger and larger, so we do
        // it anyway.
//        storedValues.clear(); // We can always reuse storedValues; it's exclusively owned by this
        storedValues = new HashMap<>();

        // Don't reuse buffers, reclaim memory so we don't run out
//        if (reuseBuffers) {
//            values.clear();
//            increments.clear();
//            payloads.clear();
//        } else {
            values = new ArrayList<>();
            increments = new IntArrayList();
            if (payloads != null) {
                payloads = new ArrayList<>();
            }
//        }
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

}
