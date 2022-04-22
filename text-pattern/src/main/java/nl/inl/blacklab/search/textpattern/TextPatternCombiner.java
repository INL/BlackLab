package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Abstract base class for combining several text patterns into a single new
 * compound TextPattern
 */
public abstract class TextPatternCombiner extends TextPattern {
    protected final List<TextPattern> clauses = new ArrayList<>();

    public TextPatternCombiner(TextPattern... clauses) {
        for (TextPattern clause : clauses) {
            addClause(clause);
        }
    }

    public int numberOfClauses() {
        return clauses.size();
    }

    @Override
    public abstract BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery;

    public final void addClause(TextPattern clause) {
        clauses.add(clause);
    }

    public void replaceClause(TextPattern oldClause, TextPattern... newClauses) {
        int i = clauses.indexOf(oldClause);
        clauses.remove(i);
        for (TextPattern newChild : newClauses) {
            clauses.add(i, newChild);
            i++;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternCombiner) {
            return clauses.equals(((TextPatternCombiner) obj).clauses);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clauses.hashCode();
    }
}
