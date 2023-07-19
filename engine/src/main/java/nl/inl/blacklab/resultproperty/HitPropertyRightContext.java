package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Hits;

/**
 * @deprecated use {@link HitPropertyAfterHit} instead
 */
@Deprecated
public class HitPropertyRightContext extends HitPropertyAfterHit {
    HitPropertyRightContext(HitPropertyAfterHit prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
    }

    public HitPropertyRightContext(BlackLabIndex index,
            Annotation annotation,
            MatchSensitivity sensitivity) {
        super(index, annotation, sensitivity);
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity,
            int numberOfTokens) {
        super(index, annotation, sensitivity, numberOfTokens);
    }
}
