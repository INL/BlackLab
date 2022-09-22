package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the context of the hit.
 */
public class HitPropertyWordRight extends HitPropertyContextBase {
    protected static final ContextSize CONTEXT_SIZE = ContextSize.get(0, 1, false);
    
    static HitPropertyWordRight deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        return deserializeProp(HitPropertyWordRight.class, index, field, info);
    }

    HitPropertyWordRight(HitPropertyWordRight prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
    }

    public HitPropertyWordRight(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        super("word right", "wordright", index, annotation, sensitivity);
    }

    public HitPropertyWordRight(BlackLabIndex index, Annotation annotation) {
        this(index, annotation, null);
    }

    public HitPropertyWordRight(BlackLabIndex index) {
        this(index, null, null);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyWordRight(this, newHits, contexts, invert);
    }

    @Override
    public PropertyValueContextWord get(long hitIndex) {
        int[] context = contexts.get(hitIndex);
        int contextRightStart = context[Contexts.RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        if (contextLength <= contextRightStart)
            return new PropertyValueContextWord(index, annotation, sensitivity, Terms.NO_TERM);
        int contextStart = contextLength * contextIndices.getInt(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        return new PropertyValueContextWord(index, annotation, sensitivity, context[contextStart + contextRightStart]);
    }

    @Override
    public int compare(long indexA, long indexB) {
        int[] ca = contexts.get(indexA);
        int caRightStart = ca[Contexts.RIGHT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get(indexB);
        int cbRightStart = cb[Contexts.RIGHT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        if (caLength <= caRightStart)
            return cbLength <= cbRightStart ? 0 : (reverse ? 1 : -1);
        if (cbLength <= cbRightStart)
            return reverse ? -1 : 1;
        // Compare one word to the right of the hit
        int contextIndex = contextIndices.getInt(0);
        int cmp = terms.compareSortPosition(
                ca[contextIndex * caLength + caRightStart + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                cb[contextIndex * cbLength + cbRightStart + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                sensitivity);
        return reverse ? -cmp : cmp;
    }

    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + CONTEXT_SIZE.hashCode();
    }
    
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return CONTEXT_SIZE;
    }
}
