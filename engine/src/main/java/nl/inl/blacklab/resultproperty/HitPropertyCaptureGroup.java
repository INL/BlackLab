package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the text actually matched. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyCaptureGroup extends HitPropertyContextBase {

    protected static final ContextSize contextSize = ContextSize.get(0,0,true);

    static HitPropertyCaptureGroup deserializeProp(BlackLabIndex index, AnnotatedField field, String info, String groupName) {
        HitPropertyCaptureGroup hp = deserializeProp(HitPropertyCaptureGroup.class, index, field, info);
        hp.groupName = groupName;
        return hp;
    }

    String groupName;

    int groupIndex = -2;

    HitPropertyCaptureGroup(HitPropertyCaptureGroup prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
        groupName = prop.groupName;
        groupIndex = prop.groupIndex;
    }

    // Used by HitPropertyContextBase.deserializeProp() (see above)
    @SuppressWarnings("unused")
    HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, "");
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String groupName) {
        super("hit text", "hit", index, annotation, sensitivity);
        this.groupName = groupName;
        groupIndex = hits.capturedGroups().names().indexOf(groupName);
        if (groupIndex < 0)
            throw new IllegalArgumentException("Unknown group name '" + groupName + "'");
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, MatchSensitivity sensitivity, String groupName) {
        this(index, null, sensitivity/*, null*/, groupName);
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, String groupName) {
        this(index, annotation, null/*, null*/, groupName);
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, String groupName) {
        this(index, null, null/*, null*/, groupName);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        HitPropertyCaptureGroup hp = new HitPropertyCaptureGroup(this, newHits, contexts, invert);
        hp.groupIndex = newHits.capturedGroups().names().indexOf(groupName);
        if (hp.groupIndex < 0)
            throw new IllegalArgumentException("Unknown group name '" + groupName + "'");
        return hp;
    }

    @Override
    public PropertyValueContextWords get(long hitIndex) {
        // Determine group start/end
        Hit hit = hits.get(hitIndex);
        Span[] capturedGroups = hits.capturedGroups().get(hit);
        Span group = capturedGroups[groupIndex];
        int start = group.start();
        int end = group.end();
        int startOfGroupWithinHit = start - hit.start();
        int endOfGroupWithinHit = end - hit.start();

        // Find context and the indexes we need
        int[] context = contexts.get(hitIndex);
        int startOfHitWithinContext = context[Contexts.HIT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        // Copy the desired part of the context
        int n = endOfGroupWithinHit - startOfGroupWithinHit;
        if (n <= 0)
            return new PropertyValueContextWords(index, annotation, sensitivity, new int[0], false);
        int[] dest = new int[n];
        int contextStart = contextLength * contextIndices.getInt(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        System.arraycopy(context, contextStart + startOfHitWithinContext + startOfGroupWithinHit, dest, 0, n);
        return new PropertyValueContextWords(index, annotation, sensitivity, dest, false);
    }

    @Override
    public int compare(long indexA, long indexB) {
        return get(indexA).compareTo(get(indexB));

        /*
        int[] ca = contexts.get(indexA);
        int caHitStart = ca[Contexts.HIT_START_INDEX];
        int caRightStart = ca[Contexts.RIGHT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get(indexB);
        int cbHitStart = cb[Contexts.HIT_START_INDEX];
        int cbRightStart = cb[Contexts.RIGHT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        // Compare the hit context for these two hits
        int contextIndex = contextIndices.getInt(0);
        int ai = caHitStart;
        int bi = cbHitStart;
        while (ai < caRightStart && bi < cbRightStart) {
            int cmp = terms.compareSortPosition(
                    ca[contextIndex * caLength + ai + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                    cb[contextIndex * cbLength + bi + Contexts.NUMBER_OF_BOOKKEEPING_INTS], sensitivity);
            if (cmp != 0)
                return reverse ? -cmp : cmp;
            ai++;
            bi++;
        }
        // One or both ran out, and so far, they're equal.
        if (ai == caRightStart) {
            if (bi != cbRightStart) {
                // b longer than a => a < b
                return reverse ? 1 : -1;
            }
            return 0; // same length; a == b
        }
        return reverse ? -1 : 1; // a longer than b => a > b
        */
    }

    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
    
    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return ContextSize.get(0, 0, true);
    }
    
    @Override
    public int hashCode() {
        return 31 * super.hashCode() + contextSize.hashCode();
    }
}
