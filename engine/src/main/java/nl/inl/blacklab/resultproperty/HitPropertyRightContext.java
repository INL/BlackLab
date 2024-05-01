package nl.inl.blacklab.resultproperty;

import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * @deprecated use {@link HitPropertyAfterHit} instead
 */
@Deprecated
public class HitPropertyRightContext extends HitPropertyAfterHit {

    public static final String ID = "right";

    static HitPropertyAfterHit deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        DeserializeInfos i = deserializeProp(field, infos);
        return new HitPropertyRightContext(index, i.annotation, i.sensitivity, i.extraIntParam(0));
    }

    public HitPropertyRightContext(BlackLabIndex index,
            Annotation annotation,
            MatchSensitivity sensitivity) {
        super(index, annotation, sensitivity, ID);
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity,
            int numberOfTokens) {
        super(index, annotation, sensitivity, numberOfTokens, ID);
    }
}
