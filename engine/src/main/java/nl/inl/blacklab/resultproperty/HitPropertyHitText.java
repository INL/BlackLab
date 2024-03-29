package nl.inl.blacklab.resultproperty;

import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the text actually matched.
 */
public class HitPropertyHitText extends HitPropertyContextBase {

    public static final String ID = "hit";

    static HitPropertyHitText deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        DeserializeInfos i = deserializeProp(field, infos);
        return new HitPropertyHitText(index, i.annotation, i.sensitivity);
    }

    HitPropertyHitText(HitPropertyHitText prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
    }

    public HitPropertyHitText(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        super("hit text", ID, index, annotation, sensitivity, false);
    }

    public HitPropertyHitText(BlackLabIndex index, MatchSensitivity sensitivity) {
        this(index, null, sensitivity);
    }

    public HitPropertyHitText(BlackLabIndex index, Annotation annotation) {
        this(index, annotation, null);
    }

    public HitPropertyHitText(BlackLabIndex index) {
        this(index, null, null);
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyHitText(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        fetchContext((int[] starts, int[] ends, int j, Hit h) -> {
            starts[j] = h.start();
            ends[j] = h.end();
        });
    }

    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
}
