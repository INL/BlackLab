package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryNot;

/**
 * NOT operator for TextPattern queries at token and sequence level. Really only
 * makes sense for 1-token clauses, as it produces all tokens that don't match
 * the clause.
 */
public class TextPatternNot extends TextPatternCombiner {
    public TextPatternNot(TextPattern clause) {
        super(clause);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryNot(clauses.get(0).translate(context));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternNot) {
            return super.equals(obj);
        }
        return false;
    }

    // appease PMD
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "NOT(" + clauses.get(0).toString() + ")";
    }
}
