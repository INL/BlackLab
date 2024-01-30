package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Hits;

/**
 * @deprecated use {@link HitPropertyBeforeHit}  instead
 */
@Deprecated
public class HitPropertyLeftContext extends HitPropertyBeforeHit {
    HitPropertyLeftContext(HitPropertyBeforeHit prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
    }

    public HitPropertyLeftContext(BlackLabIndex index,
            Annotation annotation,
            MatchSensitivity sensitivity) {
        super(index, annotation, sensitivity);
    }

    public HitPropertyLeftContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity,
            int numberOfTokens) {
        super(index, annotation, sensitivity, numberOfTokens);
    }
}
