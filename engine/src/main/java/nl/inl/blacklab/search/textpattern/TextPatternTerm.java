package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import org.apache.lucene.index.Term;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTerm extends TextPattern {

    /** Annotation to match, or null for default (usually "word"). */
    protected final String annotation;

    /** Sensitivity to match with, or null for default (insensitive, unless configured otherwise. */
    protected final MatchSensitivity sensitivity;

    protected final String value;

    public String getValue() {
        return value;
    }

    public TextPatternTerm(String value) {
        this(value, null, null);
    }

    public TextPatternTerm(String value, String annotation, MatchSensitivity sensitivity) {
        this.annotation = annotation;
        this.sensitivity = sensitivity;
        this.value = value;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        context = context.withAnnotationAndSensitivity(annotation, sensitivity);
        return new BLSpanTermQuery(context.queryInfo(), new Term(context.luceneField(),
                context.optDesensitize(optInsensitive(context, value))));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternTerm that = (TextPatternTerm) o;
        return Objects.equals(annotation, that.annotation) && sensitivity == that.sensitivity
                && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotation, sensitivity, value);
    }

    @Override
    public String toString() {
        String optAnnot = annotation == null ? "" : annotation + ", ";
        String optSensitive = sensitivity == null ? "" : ", " + sensitivity.luceneFieldSuffix();
        return "TERM(" + optAnnot + value + optSensitive + ")";
    }

    public TextPatternTerm withAnnotationAndSensitivity(String annotation, MatchSensitivity sensitivity) {
        if (annotation == null)
            annotation = this.annotation;
        if (sensitivity == null)
            sensitivity = this.sensitivity;
        return new TextPatternTerm(value, annotation, sensitivity);
    }

    public String getAnnotation() {
        return annotation;
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

    @Override
    public boolean isBracketQuery() {
        // There must be an annotation; CorpusQL can use a default annotation, but only outside brackets.
        return annotation != null;
    }
}
