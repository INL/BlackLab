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
 * A hit property for grouping on a matched group.
 */
public class HitPropertyCaptureGroup extends HitPropertyContextBase {

    protected static final ContextSize CONTEXT_SIZE = ContextSize.get(0,0,true);

    static HitPropertyCaptureGroup deserializeProp(BlackLabIndex index, AnnotatedField field, String info, String groupName) {
        HitPropertyCaptureGroup hp = deserializeProp(HitPropertyCaptureGroup.class, index, field, info);
        hp.groupName = groupName;
        return hp;
    }

    private static int findGroupIndex(Hits hits, String groupName) {
        int groupIndex = groupName.isEmpty() ? 0 : hits.capturedGroups().names().indexOf(groupName);
        if (groupIndex < 0)
            throw new IllegalArgumentException("Unknown group name '" + groupName + "'");
        return groupIndex;
    }

    String groupName;

    int groupIndex;

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
        groupIndex = findGroupIndex(hits, groupName);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        HitPropertyCaptureGroup hp = new HitPropertyCaptureGroup(this, newHits, contexts, invert);
        hp.groupIndex = findGroupIndex(newHits, groupName); // index might be different for different hits object!
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
        return 31 * super.hashCode() + CONTEXT_SIZE.hashCode();
    }
}
