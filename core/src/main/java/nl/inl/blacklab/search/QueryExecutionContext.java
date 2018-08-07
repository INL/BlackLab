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
     * @param searcher the searcher
     * @param field field to get an execution context for
     * @return the context
     */
    public static QueryExecutionContext simple(Searcher searcher, AnnotatedField field) {
        String mainPropName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        return new QueryExecutionContext(searcher, field, mainPropName, false, false);
    }

    /** The searcher object, representing the BlackLab index */
    private Searcher searcher;

    /** The (annotated) field to search */
    private AnnotatedField field;
    
    /** Annotation and optional subannotation to search */
    private String annotName;

    /** The annotation to search */
    private Annotation annotation;

    /** What to prefix values with (for "subproperties", like PoS features, etc.) */
    private String subpropPrefix;

    /**
     * Search case-sensitive?
     *
     * NOTE: depending on available alternatives in the index, this will choose the
     * most appropriate one.
     */
    private boolean caseSensitive;

    /**
     * Search diacritics-sensitive?
     *
     * NOTE: depending on available alternatives in the index, this will choose the
     * most appropriate one.
     */
    private boolean diacriticsSensitive;

    /**
     * Construct a query execution context object.
     * 
     * @param searcher the searcher object
     * @param annotation the annotation to search
     * @param caseSensitive whether search defaults to case-sensitive
     * @param diacriticsSensitive whether search defaults to diacritics-sensitive
     */
    public QueryExecutionContext(Searcher searcher, Annotation annotation, boolean caseSensitive, boolean diacriticsSensitive) {
        if (annotation == null)
            throw new IllegalArgumentException("Annotation doesn't exist: null");
        init(searcher, annotation, caseSensitive, diacriticsSensitive);
    }

    private void init(Searcher searcher, Annotation annotation, boolean caseSensitive, boolean diacriticsSensitive) {
        this.searcher = searcher;
        this.field = annotation.field();
        this.annotation = annotation;
        this.annotName = annotation.name();
        String sep = AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR;
        this.subpropPrefix = annotation.isSubannotation() ? sep + annotation.subName() + sep : "";
        this.caseSensitive = caseSensitive;
        this.diacriticsSensitive = diacriticsSensitive;
    }

    /**
     * Construct a query execution context object.
     * 
     * @param searcher the searcher object
     * @param field annotated field to search
     * @param annotName the annotation to search
     * @param caseSensitive whether search defaults to case-sensitive
     * @param diacriticsSensitive whether search defaults to diacritics-sensitive
     */
    public QueryExecutionContext(Searcher searcher, AnnotatedField field, String annotName, boolean caseSensitive,
            boolean diacriticsSensitive) {
        String[] parts = annotName.split("/", -1);
        if (parts.length > 2)
            throw new IllegalArgumentException("Annotation name contains more than one colon: " + annotName);
        Annotation annotation = field.annotations().get(parts[0]);
        if (annotation == null)
            throw new IllegalArgumentException("Annotation doesn't exist: " + annotName);
        if (parts.length > 1)
            annotation = annotation.subannotation(parts[1]);
        init(searcher, annotation, caseSensitive, diacriticsSensitive);
    }

    /**
     * Return a new query execution context with a different annotation selected.
     * 
     * @param newAnnotName the (sub)annotation to select
     * @return the new context
     */
    public QueryExecutionContext withProperty(String newAnnotName) {
        return new QueryExecutionContext(searcher, annotation.field(), newAnnotName, caseSensitive, diacriticsSensitive);
    }

    public QueryExecutionContext withSensitive(boolean caseSensitive, boolean diacriticsSensitive) {
        return new QueryExecutionContext(searcher, annotation.field(), annotName, caseSensitive, diacriticsSensitive);
    }

    public String optDesensitize(String value) {

        final String s = AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME;
        final String i = AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME;
        final String ci = AnnotatedFieldNameUtil.CASE_INSENSITIVE_ALT_NAME;
        final String di = AnnotatedFieldNameUtil.DIACRITICS_INSENSITIVE_ALT_NAME;

        String[] parts = AnnotatedFieldNameUtil.getNameComponents(luceneField());

        String alt = parts.length >= 3 ? parts[2] : "";
        if (alt.equals(s)) {
            // Don't desensitize
            return value;
        }
        if (alt.equals(i)) {
            // Fully desensitize;
            return StringUtil.stripAccents(value).toLowerCase();
        }
        if (alt.equals(ci)) {
            // Only case-insensitive
            return value.toLowerCase();
        }
        if (alt.equals(di)) {
            // Only diacritics-insensitive
            return StringUtil.stripAccents(value);
        }

        // Unknown alternative; don't change value
        return value;
    }
    
    /**
     * Return sensitivity to use
     *
     * @return sensitivity to use
     */
    private AnnotationSensitivity getSensitivity() {

        // New alternative naming scheme (every alternative has a name)
        if (!caseSensitive && !diacriticsSensitive) {
            // search insensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
        } else if (caseSensitive && diacriticsSensitive) {
            // search fully-sensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        } else if (!diacriticsSensitive) {
            // search case-sensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        } else {
            // search diacritics-sensitive if available
            if (annotation.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.CASE_INSENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.SENSITIVE))
                return annotation.sensitivity(MatchSensitivity.SENSITIVE);
            if (annotation.hasSensitivity(MatchSensitivity.INSENSITIVE))
                return annotation.sensitivity(MatchSensitivity.INSENSITIVE);
        }
        throw new RuntimeException("No suitable sensitivity found"); // should never happen
    }

    /**
     * Returns the correct current Lucene field name to use, based on the annotated
     * field name, annotation name and list of alternatives.
     * 
     * @return null if field, annotation or alternative not found; valid Lucene field
     *         name otherwise
     */
    public String luceneField() {
        return luceneField(true);
    }

    /**
     * Returns the correct current Lucene field name to use, based on the annotated
     * field name, annotation name and list of alternatives.
     * 
     * @param includeAlternative if true, also includes the default alternative at
     *            the end of the field name (alternatives determine stuff like
     *            case-/diacritics-sensitivity).
     * @return null if field, annotation or alternative not found; valid Lucene field
     *         name otherwise
     */
    public String luceneField(boolean includeAlternative) {
        if (!includeAlternative)
            return annotation.luceneFieldPrefix();
        return getSensitivity().luceneField();
    }

    /**
     * The (annotated) field to search
     * 
     * @return field name
     */
    public String fieldName() {
        return field.name();
    }

    /**
     * The annotation to search
     * 
     * @return annotation name
     */
    public String annotName() {
        return annotName;
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

    /**
     * Search diacritics-sensitive?
     *
     * NOTE: depending on available alternatives in the index, this will choose the
     * most appropriate one.
     *
     * @return true iff we want to pay attention to diacritics
     */
    public boolean diacriticsSensitive() {
        return diacriticsSensitive;
    }

    /**
     * Search case-sensitive?
     *
     * NOTE: depending on available alternatives in the index, this will choose the
     * most appropriate one.
     *
     * @return true iff we want to pay attention to case
     */
    public boolean caseSensitive() {
        return caseSensitive;
    }

    public Searcher getSearcher() {
        return searcher;
    }

}
