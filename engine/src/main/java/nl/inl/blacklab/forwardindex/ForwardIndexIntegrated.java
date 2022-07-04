package nl.inl.blacklab.forwardindex;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Multiple-annotation forward index (FIs integrated).
 *
 * This implementation works with FIs integrated into the Lucene index.
 *
 * Note that in the integrated case, there's no separate forward index id (fiid),
 * but instead the Lucene docId is used.
 */
public class ForwardIndexIntegrated extends ForwardIndexAbstract {

    public ForwardIndexIntegrated(BlackLabIndex index, AnnotatedField field) {
        super(index, field);
    }

    protected AnnotationForwardIndex openAnnotationForwardIndex(Annotation annotation) {
        AnnotationForwardIndex afi = AnnotationForwardIndexIntegrated.open(annotation, index.collator());
        add(annotation, afi);
        return afi;
    }

}
