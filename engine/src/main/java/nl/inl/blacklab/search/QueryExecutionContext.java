package nl.inl.blacklab.search;

import java.util.HashSet;
import java.util.Set;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Represents the current "execution context" for executing a TextPattern query.
 * Inside a query, this context may change: a different annotation may be
 * "selected" to search in, the case sensitivity setting may change, etc. This
 * object is passed to the translation methods and keeps track of this context.
 */
public class QueryExecutionContext {

    public static QueryExecutionContext get(BlackLabIndex index, Annotation annotation, MatchSensitivity matchSensitivity) {
        return new QueryExecutionContext(index, annotation.field().name(), null, annotation.name(),
                matchSensitivity, null, null);
    }

    public static QueryExecutionContext get(BlackLabIndex index, String field, String version, String annotation,
            MatchSensitivity sensitivity, String defaultRelationClass) {
        return new QueryExecutionContext(index, field, version, annotation, sensitivity, defaultRelationClass, null);
    }

    /** The index object, representing the BlackLab index */
    private final BlackLabIndex index;

    /**
     * Name of the annotated field we're searching.
     * Stored as a string because we might want to jump between versions
     * (e.g. different languages), and this name might not include a version and
     * therefore not be an existing field. (e.g. this might be "contents"
     * when the actual fields for different versions are named "contents__en" and "contents__nl")
     */
    private final String fieldName;

    /** Version of the document we're searching, or null if this is not a parallel corpus. */
    private final String version;

    /** Annotation to search */
    private final String annotationName;

    /** The originally requested match sensitivity.
     *
     *  This might be different from the AnnotationSensitivity we search, because not all
     *  fields support all sensitivities. */
    private final MatchSensitivity requestedSensitivity;

    /** The sensitivity variant of our annotation we'll search. */
    private final AnnotationSensitivity sensitivity;

    /** Default relation class to search */
    private final String defaultRelationClass;

    /** Registered capture names */
    private final Set<String> captures;

    /**
     * Construct a query execution context object.
     *
     * @param index the index object
     * @param fieldName the annotated field to search
     * @param version the version to search (if this is a parallel corpus; otherwise null)
     * @param annotationName the annotation to search
     * @param matchSensitivity whether search defaults to case-/diacritics-sensitive
     * @param defaultRelationClass default relation class to search (or null to use global default)
     * @param captures unique capture names assigned so far
     */
    private QueryExecutionContext(BlackLabIndex index, String fieldName, String version, String annotationName,
            MatchSensitivity matchSensitivity, String defaultRelationClass, Set<String> captures) {
        this.index = index;
        this.fieldName = version == null ? fieldName :
                AnnotatedFieldNameUtil.getParallelFieldVersion(fieldName, version);
        this.version = version;
        this.annotationName = annotationName;
        AnnotatedField field = index.annotatedField(this.fieldName);
        if (field == null)
            throw new IllegalArgumentException("Annotated field doesn't exist: null");
        Annotation annotation = field.annotation(annotationName);
        if (annotation == null)
            throw new IllegalArgumentException("Annotation doesn't exist: null");
        this.requestedSensitivity = matchSensitivity;
        sensitivity = getAppropriateSensitivity(annotation, matchSensitivity);
        this.defaultRelationClass = defaultRelationClass == null ? RelationUtil.CLASS_DEFAULT_SEARCH : defaultRelationClass;
        this.captures = captures == null ? new HashSet<>() : captures;
    }

    public QueryExecutionContext withAnnotationAndSensitivity(Annotation annotation, MatchSensitivity matchSensitivity) {
        if (annotation == null)
            annotation = sensitivity.annotation();
        if (matchSensitivity == null)
            matchSensitivity = requestedSensitivity;
        return new QueryExecutionContext(index, fieldName, version, annotation.name(), matchSensitivity,
                defaultRelationClass, captures);
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
        return withAnnotationAndSensitivity(field().annotation(name), null);
    }

    @Deprecated
    public QueryExecutionContext withXmlTagsAnnotation() {
        return withRelationAnnotation();
    }

    /**
     * Get a copy of this context for a different document version (parallel corpora).
     *
     * @param version document version to search
     * @return new context object
     */
    public QueryExecutionContext withDocVersion(String version) {
        if (version.equals(this.version))
            return this;
        return new QueryExecutionContext(index, fieldName, version, annotationName, requestedSensitivity,
                defaultRelationClass, captures);
    }

    /**
     * Get a copy of this context for a different default relation class.
     *
     * @param relClass default relation class to use if not overridden
     * @return new context object
     */
    public QueryExecutionContext withDefaultRelationClass(String relClass) {
        return new QueryExecutionContext(index, fieldName, version, annotationName, requestedSensitivity,
                relClass, captures);
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

    public String ensureUniqueCapture(String captureBaseName) {
        String capture = captureBaseName;
        int i = 2;
        while (captures.contains(capture)) {
            capture = captureBaseName + i;
            i++;
        }
        captures.add(capture);
        return capture;
    }
}
