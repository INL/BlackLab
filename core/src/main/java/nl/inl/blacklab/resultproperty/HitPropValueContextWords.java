package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.util.ArrayUtil;

public class HitPropValueContextWords extends HitPropValueContext {
    int[] valueTokenId;

    int[] valueSortOrder;

    boolean sensitive;

    public HitPropValueContextWords(Hits hits, Annotation annotation, int[] value, boolean sensitive) {
        super(hits, annotation);
        this.valueTokenId = value;
        this.sensitive = sensitive;
        valueSortOrder = new int[value.length];
        terms.toSortOrder(value, valueSortOrder, sensitive);
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
        AnnotatedField field = hits.getSearcher().annotatedField(hits.settings().concordanceField());
        String propName = parts[0];
        Annotation annotation = field.annotations().get(propName);
        boolean sensitive = parts[1].equalsIgnoreCase("s");
        int[] ids = new int[parts.length - 2];
        Terms termsObj = hits.getSearcher().getForwardIndex(annotation).getTerms();
        for (int i = 2; i < parts.length; i++) {
            ids[i - 2] = termsObj.deserializeToken(parts[i]);
        }
        return new HitPropValueContextWords(hits, annotation, ids, sensitive);
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
        parts[2] = (sensitive ? "s" : "i");
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
