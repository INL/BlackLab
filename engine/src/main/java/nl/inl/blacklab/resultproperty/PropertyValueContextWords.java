package nl.inl.blacklab.resultproperty;

import java.util.Arrays;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.ArrayUtil;

public class PropertyValueContextWords extends PropertyValueContext {
    int[] valueTokenId;

    int[] valueSortOrder;

    private MatchSensitivity sensitivity;
    
    private boolean reverseOnDisplay = false;

    public PropertyValueContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int[] value, boolean reverseOnDisplay) {
        super(index, annotation);
        this.sensitivity = sensitivity;
        this.valueTokenId = value;
        this.valueSortOrder = new int[value.length];
        terms.toSortOrder(value, valueSortOrder, sensitivity);
        this.reverseOnDisplay = reverseOnDisplay;
    }

    @Override
    public int compareTo(Object o) {
        return ArrayUtil.compareArrays(valueSortOrder, ((PropertyValueContextWords) o).valueSortOrder);
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

    public static PropertyValue deserialize(BlackLabIndex index, AnnotatedField field, String info, boolean reverseOnDisplay) {
        String[] parts = PropertySerializeUtil.splitParts(info);
        String propName = parts[0];
        Annotation annotation = field.annotation(propName);
        MatchSensitivity sensitivity = MatchSensitivity.fromLuceneFieldSuffix(parts[1]);
        int[] ids = new int[parts.length - 2];
        Terms termsObj = index.annotationForwardIndex(annotation).terms();
        for (int i = 2; i < parts.length; i++) {
            ids[i - 2] = termsObj.deserializeToken(parts[i]);
        }
        return new PropertyValueContextWords(index, annotation, sensitivity, ids, reverseOnDisplay);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if (reverseOnDisplay) {
            for (int i = valueTokenId.length - 1; i >= 0; i--) {
                int v = valueTokenId[i];
                String word = v < 0 ? "-" : terms.get(v);
                if (word.length() > 0) {
                    if (b.length() > 0)
                        b.append(" ");
                    b.append(word);
                }
            }
        } else {
            for (int v : valueTokenId) {
                String word = v < 0 ? "-" : terms.get(v);
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
        parts[1] = annotation.name();
        parts[2] = sensitivity.luceneFieldSuffix();
        for (int i = 0; i < valueTokenId.length; i++) {
            parts[i + 3] = terms.serializeTerm(valueTokenId[i]);
        }
        return PropertySerializeUtil.combineParts(parts);
    }

    @Override
    public int[] value() {
        return valueTokenId;
    }
}
