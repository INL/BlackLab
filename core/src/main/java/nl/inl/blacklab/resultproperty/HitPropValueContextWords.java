package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.ArrayUtil;

public class HitPropValueContextWords extends HitPropValueContext {
    int[] valueTokenId;

    int[] valueSortOrder;

    private MatchSensitivity sensitivity;

    public HitPropValueContextWords(Hits hits, Annotation annotation, int[] value, MatchSensitivity sensitivity) {
        super(hits, annotation);
        this.valueTokenId = value;
        this.sensitivity = sensitivity;
        valueSortOrder = new int[value.length];
        terms.toSortOrder(value, valueSortOrder, sensitivity);
    }

    public HitPropValueContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int[] value) {
        super(index, annotation);
        this.valueSortOrder = new int[value.length];
        terms.toSortOrder(value, valueSortOrder, sensitivity);
    }

    @Override
    public int compareTo(Object o) {
        return ArrayUtil.compareArrays(valueSortOrder, ((HitPropValueContextWords) o).valueSortOrder);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(valueSortOrder);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof HitPropValueContextWords)
            return Arrays.equals(valueSortOrder, ((HitPropValueContextWords) obj).valueSortOrder);
        return false;
    }

    public static HitPropValue deserialize(Hits hits, String info) {
        String[] parts = PropValSerializeUtil.splitParts(info);
        QueryInfo queryInfo = hits.queryInfo();
        AnnotatedField field = queryInfo.field();
        String propName = parts[0];
        Annotation annotation = field.annotation(propName);
        MatchSensitivity sensitivity = MatchSensitivity.fromLuceneFieldSuffix(parts[1]);
        int[] ids = new int[parts.length - 2];
        Terms termsObj = queryInfo.index().forwardIndex(annotation).terms();
        for (int i = 2; i < parts.length; i++) {
            ids[i - 2] = termsObj.deserializeToken(parts[i]);
        }
        return new HitPropValueContextWords(hits, annotation, ids, sensitivity);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int v : valueTokenId) {
            String word = v < 0 ? "-" : terms.get(v);
            if (word.length() > 0) {
                if (b.length() > 0)
                    b.append(" ");
                b.append(word);
            }
        }
        return b.toString();
    }

    @Override
    public String serialize() {
        String[] parts = new String[valueTokenId.length + 3];
        parts[0] = "cws";
        parts[1] = annotation.name();
        parts[2] = sensitivity.luceneFieldSuffix();
        for (int i = 0; i < valueTokenId.length; i++) {
            parts[i + 3] = terms.serializeTerm(valueTokenId[i]);
        }
        return PropValSerializeUtil.combineParts(parts);
    }

    @Override
    public List<String> getPropValues() {
        return Arrays.asList(this.toString());
    }
}
