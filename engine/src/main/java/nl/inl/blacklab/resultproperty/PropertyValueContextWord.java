package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class PropertyValueContextWord extends PropertyValueContext {
    final int valueTokenId;

    final int valueSortOrder;

    final MatchSensitivity sensitivity;

    public PropertyValueContextWord(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int value) {
        super(index, annotation);
        this.valueTokenId = value;
        this.sensitivity = sensitivity;
        valueSortOrder = value < 0 ? value : terms.idToSortPosition(value, sensitivity);
    }

    @Override
    public int compareTo(Object o) {
        int a = valueSortOrder, b = ((PropertyValueContextWord) o).valueSortOrder;
        return Integer.compare(a, b);
    }

    @Override
    public int hashCode() {
        return ((Integer) valueSortOrder).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof PropertyValueContextWord)
            return valueSortOrder == ((PropertyValueContextWord) obj).valueSortOrder;
        return false;
    }

    public static PropertyValue deserialize(BlackLabIndex index, AnnotatedField field, String info) {
        String[] parts = PropertySerializeUtil.splitParts(info);
        String annotationName = parts[0];
        Annotation annotation = field.annotation(annotationName);
        MatchSensitivity sensitivity = MatchSensitivity.fromLuceneFieldSuffix(parts[1]);
        String term = parts[2];
        Terms termsObj = index.annotationForwardIndex(annotation).terms();
        int termId = termsObj.deserializeToken(term);
        return new PropertyValueContextWord(index, annotation, sensitivity, termId);
    }

    // get displayable string version; note that we lowercase this if this is case-insensitive
    @Override
    public String toString() {
        return valueTokenId < 0 ? "-" : sensitivity.desensitize(terms.get(valueTokenId));
    }

    @Override
    public String serialize() {
        String token = terms.serializeTerm(valueTokenId);
        return PropertySerializeUtil.combineParts(
                "cwo", annotation.name(),
                sensitivity.luceneFieldSuffix(),
                token);
    }

    @Override
    public Integer value() {
        return valueTokenId;
    }
    
}
