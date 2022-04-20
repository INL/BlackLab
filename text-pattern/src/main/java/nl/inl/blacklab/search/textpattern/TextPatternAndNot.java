package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAndNot;

/**
 * AND (NOT) query for combining different annotations from an annotated field.
 *
 * Note that when generating a SpanQuery, the Span start and end are also
 * matched! Therefore only two hits in the same document at the same start and
 * end position will produce a match. This is useful for e.g. selecting
 * adjectives that start with a 'b' (queries on different annotation (sub)fields
 * that should apply to the same word).
 */
public class TextPatternAndNot extends TextPattern {

    protected List<TextPattern> include = new ArrayList<>();

    protected List<TextPattern> exclude = new ArrayList<>();

    public TextPatternAndNot(TextPattern... clauses) {
        this.include.addAll(Arrays.asList(clauses));
    }

    public TextPatternAndNot(List<TextPattern> includeClauses, List<TextPattern> excludeClauses) {
        include.addAll(includeClauses);
        exclude.addAll(excludeClauses);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<BLSpanQuery> chResults = new ArrayList<>(include.size());
        for (TextPattern cl : include) {
            chResults.add(cl.translate(context));
        }
        List<BLSpanQuery> chResultsNot = new ArrayList<>(exclude.size());
        for (TextPattern cl : exclude) {
            chResultsNot.add(cl.translate(context));
        }
        return new SpanQueryAndNot(chResults, chResultsNot);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAndNot) {
            return include.equals(((TextPatternAndNot) obj).include) &&
                    exclude.equals(((TextPatternAndNot) obj).exclude);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return include.hashCode() + exclude.hashCode();
    }

    @Override
    public String toString() {
        if (exclude.isEmpty())
            return "AND(" + clausesToString(include) + ")";
        return "ANDNOT([" + clausesToString(include) + "], [" + clausesToString(exclude) + "])";
    }

}
