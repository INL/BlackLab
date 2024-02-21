package nl.inl.blacklab.resultproperty;

import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on a matched group.
 */
public class HitPropertyCaptureGroup extends HitPropertyContextBase {

    public static final String ID = "capture";

    static HitPropertyCaptureGroup deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        HitPropertyCaptureGroup hp = deserializeProp(HitPropertyCaptureGroup.class, index, field, info);

        // Decode group name
        String[] parts = PropertySerializeUtil.splitParts(info);
        hp.groupName = parts.length > 2 ? parts[2] : "";

        return hp;
    }

    private String groupName;

    private int groupIndex = -1;

    HitPropertyCaptureGroup(HitPropertyCaptureGroup prop, Hits hits, boolean invert) {
        super(prop, hits, invert, determineMatchInfoField(hits, prop.groupName));
        groupName = prop.groupName;

        // Determine group index. We don't use the one from prop (if any), because
        // index might be different for different hits object.
        groupIndex = groupName.isEmpty() ? 0 : this.hits.matchInfoIndex(groupName);
        if (groupIndex < 0)
            throw new IllegalArgumentException("Unknown group name '" + groupName + "'");
    }

    /**
     * Determine what field the given match info is from.
     *
     * Only relevant for parallel corpora, where you can capture information from
     * other fields.
     *
     * @param hits     the hits object
     * @param groupName the match info group name
     * @return the field name
     */
    private static String determineMatchInfoField(Hits hits, String groupName) {
        return hits.matchInfoDefs().stream()
                .filter(d -> d.getName().equals(groupName))
                .map(d -> d.getField())
                .findFirst().orElse(null);
    }

    // Used by HitPropertyContextBase.deserializeProp() via reflection
    @SuppressWarnings("unused")
    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, "");
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String groupName) {
        super("captured group", ID, index, annotation, sensitivity, false);
        this.groupName = groupName;
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyCaptureGroup(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        fetchContext((int[] starts, int[] ends, int indexInArrays, Hit hit) -> {
            MatchInfo group = hit.matchInfo()[groupIndex];
            starts[indexInArrays] = group.getSpanStart();
            ends[indexInArrays] = group.getSpanEnd();
        });
    }

    @Override
    public boolean isDocPropOrHitText() {
        // we cannot guarantee that we don't use any surrounding context!
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
        HitPropertyCaptureGroup that = (HitPropertyCaptureGroup) o;
        return Objects.equals(groupName, that.groupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupName);
    }
}
