package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.StringUtil;

/**
 * Represents the current "execution context" for executing a TextPattern query.
 * Inside a query, this context may change: a different annotation may be
 * "selected" to search in, the case sensitivity setting may change, etc. This
 * object is passed to the translation methods and keeps track of this context.
 */
public class QueryExecutionContext {
    /** The searcher object, representing the BlackLab index */
    private Searcher searcher;

    /** The (annotated) field to search */
    private String fieldName;

    /** The annotation to search */
    private String annotName;

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
     * @param fieldName the (annotated) field to search
     * @param annotName the annotation to search
     * @param caseSensitive whether search defaults to case-sensitive
     * @param diacriticsSensitive whether search defaults to diacritics-sensitive
     */
    public QueryExecutionContext(Searcher searcher, String fieldName, String annotName, boolean caseSensitive,
            boolean diacriticsSensitive) {
        this.searcher = searcher;
        this.fieldName = fieldName;
        String[] parts = annotName.split("/", -1);
        if (parts.length > 2)
            throw new IllegalArgumentException("propName contains more than one colon!");
        this.annotName = parts[0];
        String sep = AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR;
        this.subpropPrefix = parts.length == 2 ? sep + parts[1] + sep : "";
        this.caseSensitive = caseSensitive;
        this.diacriticsSensitive = diacriticsSensitive;
    }

    /**
     * Return a new query execution context with a different annotation selected.
     * 
     * @param newAnnotName the annotation to select
     * @return the new context
     */
    public QueryExecutionContext withProperty(String newAnnotName) {
        return new QueryExecutionContext(searcher, fieldName, newAnnotName, caseSensitive, diacriticsSensitive);
    }

    public QueryExecutionContext withSensitive(boolean caseSensitive, boolean diacriticsSensitive) {
        return new QueryExecutionContext(searcher, fieldName, annotName, caseSensitive, diacriticsSensitive);
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
     * Return alternatives for the current field/prop that exist and are appropriate
     * for our current settings.
     *
     * @return the alternatives that exist, in order of appropriateness
     */
    private String[] getSensitivities() {

        if (searcher.getClass().getName().endsWith("MockSearcher")) {
            // TODO: give MockSearcher an index structure so we don't need this hack
            if (caseSensitive)
                return new String[] { AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME, AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME };
            return new String[] { AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME, AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME };
        }

        AnnotatedField cfd = searcher.getIndexMetadata().annotatedFields().get(fieldName);
        if (cfd == null)
            return null;

        // Find the annotation
        Annotation pd = cfd.annotations().get(annotName);

        // New alternative naming scheme (every alternative has a name)
        List<String> validAlternatives = new ArrayList<>();
        if (!caseSensitive && !diacriticsSensitive) {
            // search insensitive if available
            if (pd.hasSensitivity(MatchSensitivity.INSENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME);
            if (pd.hasSensitivity(MatchSensitivity.SENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME);
        } else if (caseSensitive && diacriticsSensitive) {
            // search fully-sensitive if available
            if (pd.hasSensitivity(MatchSensitivity.SENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME);
            if (pd.hasSensitivity(MatchSensitivity.INSENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME);
        } else if (!diacriticsSensitive) {
            // search case-sensitive if available
            if (pd.hasSensitivity(MatchSensitivity.DIACRITICS_INSENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.DIACRITICS_INSENSITIVE_ALT_NAME);
            if (pd.hasSensitivity(MatchSensitivity.SENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME);
            if (pd.hasSensitivity(MatchSensitivity.INSENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME);
        } else {
            // search diacritics-sensitive if available
            if (pd.hasSensitivity(MatchSensitivity.CASE_INSENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.DIACRITICS_INSENSITIVE_ALT_NAME);
            if (pd.hasSensitivity(MatchSensitivity.SENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.SENSITIVE_ALT_NAME);
            if (pd.hasSensitivity(MatchSensitivity.INSENSITIVE))
                validAlternatives.add(AnnotatedFieldNameUtil.INSENSITIVE_ALT_NAME);
        }
        return validAlternatives.toArray(new String[] {});
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

        // Determine available alternatives based on sensitivity preferences.
        String[] alternatives = includeAlternative ? getSensitivities() : null;

        if (searcher.getClass().getName().endsWith("MockSearcher")) {
            // Mostly for testing. Don't check, just combine field parts.
            // TODO: give MockSearcher an index structure so we don't need this hack
            if (alternatives == null || alternatives.length == 0)
                return AnnotatedFieldNameUtil.annotationField(fieldName, annotName);
            return AnnotatedFieldNameUtil.annotationField(fieldName, annotName, alternatives[0]);
        }

        // Find the field and the annotation.
        AnnotatedField cfd = searcher.getIndexMetadata().annotatedFields().get(fieldName);
        if (cfd == null)
            return null;

        if (AnnotatedFieldNameUtil.isBookkeepingSubfield(annotName)) {
            // Not a annotation but a bookkeeping subfield (prob. starttag/endtag); ok, return it
            // (can be removed when old field naming scheme is removed)
            return AnnotatedFieldNameUtil.bookkeepingField(fieldName, annotName);
        }

        // Find the annotation
        Annotation pd = cfd.annotations().get(annotName);
        if (pd == null)
            return AnnotatedFieldNameUtil.annotationField(fieldName, annotName); // doesn't exist? use plain annotation name

        if (alternatives == null || alternatives.length == 0) {
            // Don't use any alternatives
            return AnnotatedFieldNameUtil.annotationField(fieldName, annotName);
        }

        // Find the first available alternative to use
        for (String alt : alternatives) {
            if (pd.hasSensitivity(MatchSensitivity.fromLuceneFieldCode(alt))) {
                // NOTE: is this loop necessary at all? getAlternatives() only
                //  returns available alternatives, so the first one should always
                //  be okay, right?
                return AnnotatedFieldNameUtil.annotationField(fieldName, annotName, alt);
            }
        }

        // No valid alternative found. Use plain annotation.
        // NOTE: should never happen, and doesn't make sense anymore as there are
        // no 'plain properties' anymore.
        return AnnotatedFieldNameUtil.annotationField(fieldName, annotName);
    }

    /**
     * Get a simple execution context for a field. Used for testing/debugging
     * purposes.
     *
     * @param searcher the searcher
     * @param fieldName field to get an execution context for
     * @return the context
     */
    public static QueryExecutionContext getSimple(Searcher searcher, String fieldName) {
        String mainPropName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        return new QueryExecutionContext(searcher, fieldName, mainPropName, false, false);
    }

    /**
     * The (annotated) field to search
     * 
     * @return field name
     */
    public String fieldName() {
        return fieldName;
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
