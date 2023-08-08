package nl.inl.blacklab.search;

import java.util.concurrent.atomic.AtomicInteger;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Represents the current "execution context" for executing a TextPattern query.
 * Inside a query, this context may change: a different annotation may be
 * "selected" to search in, the case sensitivity setting may change, etc. This
 * object is passed to the translation methods and keeps track of this context.
 */
public class QueryExecutionContext {

    /** The index object, representing the BlackLab index */
    private final BlackLabIndex index;

    /** The sensitivity variant of our annotation we'll search. */
    private final AnnotationSensitivity sensitivity;

    /** The originally requested match sensitivity.
     *
     *  This might be different from the AnnotationSensitivity we search, because not all
     *  fields support all sensitivities. */
    private final MatchSensitivity requestedSensitivity;

    /**
     * Construct a query execution context object.
     * 
     * @param index the index object
     * @param annotation the annotation to search
     * @param matchSensitivity whether search defaults to case-/diacritics-sensitive
     */
    public QueryExecutionContext(BlackLabIndex index, Annotation annotation, MatchSensitivity matchSensitivity) {
        if (annotation == null)
            throw new IllegalArgumentException("Annotation doesn't exist: null");
        this.index = index;
        this.requestedSensitivity = matchSensitivity;
        sensitivity = getAppropriateSensitivity(annotation, matchSensitivity);
    }

    public QueryExecutionContext withAnnotation(Annotation annotation) {
        return withAnnotationAndSensitivity(annotation, null);
    }

    public QueryExecutionContext withSensitivity(MatchSensitivity matchSensitivity) {
        return withAnnotationAndSensitivity((Annotation)null, matchSensitivity);
    }

    public QueryExecutionContext withAnnotationAndSensitivity(Annotation annotation, MatchSensitivity matchSensitivity) {
        if (annotation == null)
            annotation = sensitivity.annotation();
        if (matchSensitivity == null)
            matchSensitivity = requestedSensitivity;
        return new QueryExecutionContext(index, annotation, matchSensitivity);
    }

    public QueryExecutionContext withAnnotationAndSensitivity(String annotationName, MatchSensitivity matchSensitivity) {
        Annotation annotation = annotationName == null ? null : field().annotation(annotationName);
        return withAnnotationAndSensitivity(annotation, matchSensitivity);
    }

    public QueryExecutionContext withRelationAnnotation() {
        if (!field().hasXmlTags())
            throw new RuntimeException("Field has no relation annotation!");
        String name = AnnotatedFieldNameUtil.relationAnnotationName(index.getType());
        if (field().annotation(name) == null)
            throw new RuntimeException("Field has no relation annotation named " + name + "!");
        return withAnnotation(field().annotation(name));
    }

    @Deprecated
    public QueryExecutionContext withXmlTagsAnnotation() {
        return withRelationAnnotation();
    }

    public String optDesensitize(String value) {
        return sensitivity.sensitivity().desensitize(value);
    }
    
    /**
     * Return sensitivity to use.
     *
     * Because not all annotations have all sensitivities, we may have to fall back to a different one.
     *
     * @param annotation annotation to search
     * @param requestedSensitivity what sensitivity we'd ideally like
     * @return sensitivity to use
     */
    private static AnnotationSensitivity getAppropriateSensitivity(Annotation annotation,
            MatchSensitivity requestedSensitivity) {
        switch (requestedSensitivity) {
        case INSENSITIVE:
            // search insensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
            return annotation.sensitivity(MatchSensitivity.SENSITIVE);
        case SENSITIVE:
            // search fully-sensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        case DIACRITICS_INSENSITIVE:
            // search diacritics-insensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        case CASE_INSENSITIVE:
        default:
            // search case-insensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.CASE_INSENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        }
    }

    /**
     * Returns the correct current Lucene field name to use, based on the annotated
     * field name, annotation name and list of alternatives.
     * 
     * @return null if field, annotation or alternative not found; valid Lucene field
     *         name otherwise
     */
    public String luceneField() {
        return sensitivity.luceneField();
    }

    public BlackLabIndex index() {
        return index;
    }

    /**
     * Return the annotated field we're searching.
     * 
     * @return annotated field
     */
    public AnnotatedField field() {
        return sensitivity.annotation().field();
    }

    public QueryInfo queryInfo() {
        return QueryInfo.create(index(), field());
    }

    /** We need to be able to get unique ids per query, e.g. to auto-number relations captures if you
     *  don't explicitly name your captures. */
    private AtomicInteger uniqueIdCounter = new AtomicInteger();

    public int nextUniqueId() {
        return uniqueIdCounter.getAndIncrement();
    }
}
