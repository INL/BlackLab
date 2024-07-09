package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.util.PropertySerializeUtil;

public class PropertyValueContextWords extends PropertyValueContext {

    /** String to use to indicate there was no value.
     *
     * For example: you're grouping by the word left of the match, but the
     * match occurred at the start of the document.
     */
    public static final String NO_VALUE_STR = "(no value)";

    /** Term ids for this value */
    int[] valueTokenId;

    /** Sort orders for our term ids */
    final int[] valueSortOrder;

    /** Sensitivity to use for comparisons */
    private MatchSensitivity sensitivity;

    /**
     * With context before of the match, sorting/grouping occurs from
     * front to back (e.g. right to left for English), but display should still
     * be from back to front.
     */
    private boolean reverseOnDisplay;

    public PropertyValueContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int[] value, boolean reverseOnDisplay) {
        this(index.annotationForwardIndex(annotation).terms(), annotation.name(), sensitivity, value, reverseOnDisplay);
    }

    public PropertyValueContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int[] value, int[] sortOrder, boolean reverseOnDisplay) {
        this(index.annotationForwardIndex(annotation).terms(), annotation.name(), sensitivity, value, sortOrder, reverseOnDisplay);
    }

    public PropertyValueContextWords(Terms terms, String annotationName, MatchSensitivity sensitivity, int[] value, boolean reverseOnDisplay) {
        super(terms, annotationName);
        this.sensitivity = sensitivity;
        this.valueTokenId = value;
        this.valueSortOrder = new int[value.length];
        terms.toSortOrder(value, valueSortOrder, sensitivity);
        this.reverseOnDisplay = reverseOnDisplay;
    }

    public PropertyValueContextWords(Terms terms, String annotationName, MatchSensitivity sensitivity, int[] value, int[] sortOrder, boolean reverseOnDisplay) {
        super(terms, annotationName);
        this.sensitivity = sensitivity;
        this.valueTokenId = value;
        this.valueSortOrder = sortOrder;
        this.reverseOnDisplay = reverseOnDisplay;
    }

    public static PropertyValueContextWords empty(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        return new PropertyValueContextWords(index, annotation, sensitivity, new int[0], false);
    }

    @Override
    public int compareTo(Object o) {
        return Arrays.compare(valueSortOrder, ((PropertyValueContextWords) o).valueSortOrder);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(valueSortOrder);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof PropertyValueContextWords)
            return Arrays.equals(valueSortOrder, ((PropertyValueContextWords) obj).valueSortOrder);
        return false;
    }

    public static PropertyValue deserialize(BlackLabIndex index, AnnotatedField field, List<String> infos, boolean reverseOnDisplay) {
        String propName = infos.isEmpty() ? field.mainAnnotation().name() : infos.get(0);
        Annotation annotation = field.annotation(propName);
        MatchSensitivity sensitivity = infos.size() > 1 ? MatchSensitivity.fromLuceneFieldSuffix(infos.get(1)) :
                annotation.mainSensitivity().sensitivity();
        int[] ids = new int[infos.size() - 2];
        Terms termsObj = index.annotationForwardIndex(annotation).terms();
        for (int i = 2; i < infos.size(); i++) {
            ids[i - 2] = deserializeToken(termsObj, infos.get(i));
        }
        return new PropertyValueContextWords(index, annotation, sensitivity, ids, reverseOnDisplay);
    }

    public static PropertyValue deserializeSingleWord(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        String annotationName = infos.isEmpty() ? field.mainAnnotation().name() : infos.get(0);
        Annotation annotation = field.annotation(annotationName);
        MatchSensitivity sensitivity = infos.size() > 1 ? MatchSensitivity.fromLuceneFieldSuffix(infos.get(1)) :
                annotation.mainSensitivity().sensitivity();
        String term = infos.size() > 2 ? infos.get(2) : "";
        Terms termsObj = index.annotationForwardIndex(annotation).terms();
        int termId = deserializeToken(termsObj, term);
        return new PropertyValueContextWords(index, annotation, sensitivity, new int[] { termId }, false);
    }

    // get displayable string version; note that we lowercase this if this is case-insensitive
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if (reverseOnDisplay) {
            for (int i = valueTokenId.length - 1; i >= 0; i--) {
                int v = valueTokenId[i];
                String word = v < 0 ? NO_VALUE_STR : sensitivity.desensitize(terms.get(v));
                if (word.length() > 0) {
                    if (b.length() > 0)
                        b.append(" ");
                    b.append(word);
                }
            }
        } else {
            for (int v : valueTokenId) {
                String word = v < 0 ? NO_VALUE_STR : sensitivity.desensitize(terms.get(v));
                if (word.length() > 0) {
                    if (b.length() > 0)
                        b.append(" ");
                    b.append(word);
                }
            }
        }
        return b.toString();
    }

    @Override
    public String serialize() {
        String[] parts = new String[valueTokenId.length + 3];
        parts[0] = reverseOnDisplay ? "cwsr" : "cws";
        parts[1] = annotationName;
        parts[2] = sensitivity.luceneFieldSuffix();
        for (int i = 0; i < valueTokenId.length; i++) {
            String term = serializeTerm(terms, valueTokenId[i]);
            parts[i + 3] = term;
        }
        return PropertySerializeUtil.combineParts(parts);
    }

    @Override
    public int[] value() {
        return valueTokenId;
    }
}
