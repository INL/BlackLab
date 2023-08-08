package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAndNot;

/**
 * AND operation.
 * 
 * Actually just TextPatternAndNot without the option of specifying a NOT part.
 */
public class TextPatternAnd extends TextPattern {

    protected final List<TextPattern> clauses = new ArrayList<>();

    public TextPatternAnd(TextPattern... clauses) {
        this(Arrays.asList(clauses));
    }

    public TextPatternAnd(List<TextPattern> clauses) {
        if (clauses.isEmpty())
            throw new IllegalArgumentException("Must have at least one clause");
        for (TextPattern clause: clauses) {
            if (clause instanceof TextPatternAnd) {
                // Flatten nested ANDs
                this.clauses.addAll(((TextPatternAnd) clause).clauses);
            } else {
                this.clauses.add(clause);
            }
        }
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<BLSpanQuery> chResults = new ArrayList<>(clauses.size());
        for (TextPattern cl : clauses) {
            chResults.add(cl.translate(context));
        }
        return new SpanQueryAndNot(chResults, null);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAnd) {
            return clauses.equals(((TextPatternAnd) obj).clauses);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clauses.hashCode();
    }

    @Override
    public String toString() {
        return "AND(" + clausesToString(clauses) + ")";
    }

    public List<TextPattern> getClauses() {
        return clauses;
    }

    @Override
    public boolean isBracketQuery() {
        return clauses.stream().allMatch(TextPattern::isBracketQuery);
    }
}
