package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.results.Hits;

public class HitPropValueContextWord extends HitPropValueContext {
    int valueTokenId;

    int valueSortOrder;

    boolean sensitive;

    public HitPropValueContextWord(Hits hits, String propName, int value, boolean sensitive) {
        super(hits, propName);
        this.valueTokenId = value;
        this.sensitive = sensitive;
        valueSortOrder = value < 0 ? value : terms.idToSortPosition(value, sensitive);
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
        String fieldName = hits.settings().concordanceField();
        String propName = parts[0];
        boolean sensitive = parts[1].equalsIgnoreCase("s");
        String term = parts[2];
        Terms termsObj = hits.getSearcher().getForwardIndex(ComplexFieldUtil.propertyField(fieldName, propName))
                .getTerms();
        int termId = termsObj.deserializeToken(term);
        return new HitPropValueContextWord(hits, propName, termId, sensitive);
    }

    @Override
    public String toString() {
        return valueTokenId < 0 ? "-" : terms.get(valueTokenId);
    }

    @Override
    public String serialize() {
        String token = terms.serializeTerm(valueTokenId);
        return PropValSerializeUtil.combineParts(
                "cwo", propName,
                (sensitive ? "s" : "i"),
                token);
    }

    @Override
    public List<String> getPropValues() {
        return Arrays.asList(this.toString());
    }
}
