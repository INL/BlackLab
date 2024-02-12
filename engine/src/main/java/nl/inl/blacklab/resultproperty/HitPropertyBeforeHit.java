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
 * A hit property for sorting on a number of tokens before a hit.
 */
public class HitPropertyBeforeHit extends HitPropertyContextBase {

    public static final String ID = "before";

    /** How many tokens of context-before to compare */
    protected int numberOfTokens;

    static HitPropertyBeforeHit deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        return deserializeProp(HitPropertyBeforeHit.class, index, field, info);
    }

    static HitPropertyBeforeHit deserializePropSingleWord(BlackLabIndex index, AnnotatedField field, String info) {
        HitPropertyBeforeHit hitProperty = deserializeProp(HitPropertyBeforeHit.class, index, field, info);
        hitProperty.numberOfTokens = 1;
        return hitProperty;
    }

    HitPropertyBeforeHit(HitPropertyBeforeHit prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        this.numberOfTokens = prop.numberOfTokens;
    }

    // Used by HitPropertyContextBase.deserializeProp() via reflection
    public HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, -1, ID);
    }

    public HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens) {
        this(index, annotation, sensitivity, numberOfTokens, ID);
    }

    // Used by HitPropertyContextBase.deserializeProp() via reflection
    HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String serializeName) {
        this(index, annotation, sensitivity, -1, serializeName);
    }

    HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens, String serializeName) {
        super("context before", serializeName, index, annotation, sensitivity, true);
        this.numberOfTokens = numberOfTokens >= 1 ? numberOfTokens : index.defaultContextSize().before();
    }

    @Override
    void deserializeParam(String param) {
        try {
            numberOfTokens = Integer.parseInt(param);
        } catch (NumberFormatException e) {
            numberOfTokens = index.defaultContextSize().before();
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
        return new HitPropertyBeforeHit(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        fetchContext((int[] starts, int[] ends, int j, Hit h) -> {
            starts[j] = Math.max(0, h.start() - numberOfTokens);
            ends[j] = h.start();
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
        HitPropertyBeforeHit that = (HitPropertyBeforeHit) o;
        return numberOfTokens == that.numberOfTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), numberOfTokens);
    }
}
