package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on a matched group.
 */
public class HitPropertyCaptureGroup extends HitPropertyContextBase2 {

    private AnnotationForwardIndex afi;

    static HitPropertyCaptureGroup deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        HitPropertyCaptureGroup hp = deserializeProp(HitPropertyCaptureGroup.class, index, field, info);

        // Decode group name
        String[] parts = PropertySerializeUtil.splitParts(info);
        hp.groupName = parts.length > 2 ? parts[2] : "";

        return hp;
    }

    String groupName;

    int groupIndex = -1;

    HitPropertyCaptureGroup(HitPropertyCaptureGroup prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
        groupName = prop.groupName;

        // Determine group index. We don't use the one from prop (if any), because
        // index might be different for different hits object.
        groupIndex = groupName.isEmpty() ? 0 : this.hits.matchInfoNames().indexOf(groupName);
        if (groupIndex < 0)
            throw new IllegalArgumentException("Unknown group name '" + groupName + "'");
        initForwardIndex();
    }

    // Used by HitPropertyContextBase2.deserializeProp() (see above)
    @SuppressWarnings("unused")
    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, "");
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String groupName) {
        super("captured group", "capture", index, annotation, sensitivity);
        this.groupName = groupName;
        initForwardIndex();
    }

    void initForwardIndex() {
        if (hits != null) {
            ForwardIndex fi = index.forwardIndex(hits.queryInfo().field());
            afi = fi.get(annotation);
        }
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyCaptureGroup(this, newHits, contexts, invert);
    }

    @Override
    public PropertyValueContextWords get(long hitIndex) {
        // Determine group start/end
        Hit hit = hits.get(hitIndex);
        MatchInfo[] matchInfo = hit.matchInfo();
        MatchInfo group = matchInfo[groupIndex];
        int start = group.getSpanStart();
        int end = group.getSpanEnd();
        int n = end - start;
        if (n <= 0)
            return new PropertyValueContextWords(index, annotation, sensitivity, new int[0], false);
        List<int[]> result = afi.retrievePartsInt(hit.doc(), new int[] { start },
                new int[] { end }); // OPT: avoid creating arrays?
        return new PropertyValueContextWords(index, annotation, sensitivity, result.get(0), false);
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
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertyCaptureGroup that = (HitPropertyCaptureGroup) o;
        return Objects.equals(groupName, that.groupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupName);
    }
}
