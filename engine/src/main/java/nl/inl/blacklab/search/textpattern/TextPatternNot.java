package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryNot;

/**
 * NOT operator for TextPattern queries at token and sequence level. Really only
 * makes sense for 1-token clauses, as it produces all tokens that don't match
 * the clause.
 */
public class TextPatternNot extends TextPattern {

    TextPattern clause;

    public TextPatternNot(TextPattern clause) {
        this.clause = clause;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryNot(clause.translate(context));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternNot that = (TextPatternNot) o;
        return Objects.equals(clause, that.clause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause);
    }

    @Override
    public String toString() {
        return "NOT(" + clause + ")";
    }

    public TextPattern getClause() {
        return clause;
    }

    @Override
    public boolean isBracketQuery() {
        return clause.isBracketQuery();
    }
}
