package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Forward index implementation for external forward index files.
 *
 * External forward indexes are a legacy format. They will be deprecated and eventually removed.
 */
public class ForwardIndexExternal extends ForwardIndexAbstract {

    /** For common annotations, always build term indexes right away. For less common ones, do it on demand. Saves memory and startup time. */
    protected static final Set<String> BUILD_TERMINDEXES_ON_INIT = new HashSet<>(
            AnnotatedFieldNameUtil.COMMON_ANNOTATIONS);

    public ForwardIndexExternal(BlackLabIndex index, AnnotatedField field) {
        super(index, field);
    }

    private static File determineAfiDir(File indexDir, Annotation annotation) {
        return new File(indexDir, "fi_" + annotation.luceneFieldPrefix());
    }

    protected AnnotationForwardIndex openAnnotationForwardIndex(Annotation annotation) {
        File dir = determineAfiDir(index.indexDirectory(), annotation);
        boolean create = index.indexMode() && index.isEmpty();
        AnnotationForwardIndex afi = AnnotationForwardIndexExternalAbstract.open(dir, index.indexMode(), index.collator(), create, annotation);
        add(annotation, afi);
        return afi;
    }

}
