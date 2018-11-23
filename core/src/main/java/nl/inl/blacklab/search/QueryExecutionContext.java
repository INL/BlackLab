package nl.inl.blacklab.search;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.StringUtil;

/**
 * Represents the current "execution context" for executing a TextPattern query.
 * Inside a query, this context may change: a different annotation may be
 * "selected" to search in, the case sensitivity setting may change, etc. This
 * object is passed to the translation methods and keeps track of this context.
 */
public class QueryExecutionContext {
    /**
     * Get a simple execution context for a field. Used for testing/debugging
     * purposes.
     *
     * @param index the index
     * @param field field to get an execution context for
     * @return the context
     */
    public static QueryExecutionContext simple(BlackLabIndex index, AnnotatedField field) {
        return new QueryExecutionContext(index, field.mainAnnotation(), MatchSensitivity.INSENSITIVE);
    }

    /** The index object, representing the BlackLab index */
    private BlackLabIndex index;

    /** What to prefix values with (for "subproperties", like PoS features, etc.) */
    private String subpropPrefix;

    /** The sensitivity variant of our annotation we'll search. */
    private AnnotationSensitivity sensitivity;
    
    /** The originally requested match sensitivity.
     * 
     *  This might be different from the AnnotationSensitivity we search, because not all 
     *  fields support all sensitivities. */
    private MatchSensitivity requestedSensitivity;

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
        this.subpropPrefix = annotation.subpropValuePrefix();
        this.requestedSensitivity = matchSensitivity;
        sensitivity = getAppropriateSensitivity(annotation, matchSensitivity);
    }

    public QueryExecutionContext withAnnotation(Annotation annotation) {
        return new QueryExecutionContext(index, annotation, requestedSensitivity);
    }

    public QueryExecutionContext withSensitive(MatchSensitivity matchSensitivity) {
        return new QueryExecutionContext(index, sensitivity.annotation(), matchSensitivity);
    }

    public QueryExecutionContext withXmlTagsAnnotation() {
        Annotation annotation = sensitivity.annotation().field().annotation(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME);
        return new QueryExecutionContext(index, annotation, requestedSensitivity);
    }

    public String optDesensitize(String value) {
        MatchSensitivity matchSensitity = sensitivity.sensitivity();
        switch (matchSensitity) {
        case INSENSITIVE:
            // Fully desensitize;
            return StringUtil.stripAccents(value).toLowerCase();
        case CASE_INSENSITIVE:
            // Only case-insensitive
            return value.toLowerCase();
        case DIACRITICS_INSENSITIVE:
            // Only diacritics-insensitive
            return StringUtil.stripAccents(value);
        case SENSITIVE:
        default:
            // Don't desensitize
            return value;
        }
    }
    
    /**
     * Return sensitivity to use
     *
     * @return sensitivity to use
     */
    private static AnnotationSensitivity getAppropriateSensitivity(Annotation annotation, MatchSensitivity matchSensitivity) {
        switch (matchSensitivity) {
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

    /**
     * What to prefix values with (for "subannotations", like PoS features, etc.)
     *
     * Subannotations are indexed with this prefix before every value. When
     * searching, we also prefix our search string with this value.
     *
     * @return prefix what to prefix search strings with to find the right
     *         subannotation value
     */
    public String subannotPrefix() {
        return subpropPrefix;
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

}
