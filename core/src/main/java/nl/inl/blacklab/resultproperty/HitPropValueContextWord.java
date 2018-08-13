package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;

public class HitPropValueContextWord extends HitPropValueContext {
    int valueTokenId;

    int valueSortOrder;

    MatchSensitivity sensitivity;

    public HitPropValueContextWord(Hits hits, Annotation annotation, int value, MatchSensitivity sensitivity) {
        super(hits, annotation);
        this.valueTokenId = value;
        this.sensitivity = sensitivity;
        valueSortOrder = value < 0 ? value : terms.idToSortPosition(value, sensitivity);
    }

    @Override
    public int compareTo(Object o) {
        int a = valueSortOrder, b = ((HitPropValueContextWord) o).valueSortOrder;
        return a == b ? 0 : (a < b ? -1 : 1);
    }

    @Override
    public int hashCode() {
        return ((Integer) valueSortOrder).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof HitPropValueContextWord)
            return valueSortOrder == ((HitPropValueContextWord) obj).valueSortOrder;
        return false;
    }

    public static HitPropValue deserialize(Hits hits, String info) {
        String[] parts = PropValSerializeUtil.splitParts(info);
        QueryInfo queryInfo = hits.queryInfo();
        AnnotatedField field = queryInfo.field();
        String propName = parts[0];
        Annotation annotation = field.annotations().get(propName);
        MatchSensitivity sensitivity = MatchSensitivity.fromLuceneFieldSuffix(parts[1]);
        String term = parts[2];
        Terms termsObj = queryInfo.index().forwardIndex(annotation).terms();
        int termId = termsObj.deserializeToken(term);
        return new HitPropValueContextWord(hits, annotation, termId, sensitivity);
    }

    @Override
    public String toString() {
        return valueTokenId < 0 ? "-" : terms.get(valueTokenId);
    }

    @Override
    public String serialize() {
        String token = terms.serializeTerm(valueTokenId);
        return PropValSerializeUtil.combineParts(
                "cwo", annotation.name(),
                sensitivity.luceneFieldSuffix(),
                token);
    }

    @Override
    public List<String> getPropValues() {
        return Arrays.asList(this.toString());
    }
}
