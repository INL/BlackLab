package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for sorting on a number of tokens after a hit.
 */
public class HitPropertyAfterHit extends HitPropertyContextBase {

    public static final String ID = "after";

    /** How many tokens of context-after to compare */
    protected int numberOfTokens;

    static HitPropertyAfterHit deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        return deserializeProp(HitPropertyAfterHit.class, index, field, info);
    }

    static HitPropertyAfterHit deserializePropSingleWord(BlackLabIndex index, AnnotatedField field, String info) {
        HitPropertyAfterHit hitProp = deserializeProp(HitPropertyAfterHit.class, index, field, info);
        hitProp.numberOfTokens = 1;
        return hitProp;
    }

    HitPropertyAfterHit(HitPropertyAfterHit prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        this.numberOfTokens = prop.numberOfTokens;
    }

    // Used by HitPropertyContextBase.deserializeProp() via reflection
    @SuppressWarnings("unused")
    public HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, -1, ID);
    }

    public HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens) {
        this(index, annotation, sensitivity, numberOfTokens, ID);
    }

    // Used by HitPropertyContextBase.deserializeProp() via reflection
    @SuppressWarnings("unused")
    HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String serializeName) {
        this(index, annotation, sensitivity, -1, serializeName);
    }

    HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens, String serializeName) {
        super("context after", serializeName, index, annotation, sensitivity, false);
        this.numberOfTokens = numberOfTokens >= 1 ? numberOfTokens : index.defaultContextSize().after();
    }

    @Override
    void deserializeParam(String param) {
        try {
            numberOfTokens = Integer.parseInt(param);
        } catch (NumberFormatException e) {
            numberOfTokens = index.defaultContextSize().after();
        }
    }

    @Override
    public List<String> serializeParts() {
        List<String> result = new ArrayList<>(super.serializeParts());
        result.add(Integer.toString(numberOfTokens));
        return result;
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyAfterHit(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        fetchContext((int[] starts, int[] ends, int j, Hit h) -> {
            starts[j] = h.end();
            ends[j] = h.end() + numberOfTokens;
        });
    }

    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertyAfterHit that = (HitPropertyAfterHit) o;
        return numberOfTokens == that.numberOfTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), numberOfTokens);
    }
}
