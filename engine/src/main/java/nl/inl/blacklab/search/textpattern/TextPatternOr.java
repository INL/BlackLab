package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanOrQuery;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A TextPattern matching at least one of its child clauses.
 */
public class TextPatternOr extends TextPattern {

    List<TextPattern> clauses = new ArrayList<>();

    public TextPatternOr(TextPattern... clauses) {
        this(Arrays.asList(clauses));
    }

    public TextPatternOr(List<TextPattern> clauses) {
        if (clauses.isEmpty())
            throw new IllegalArgumentException("Must have at least one clause");
        for (TextPattern clause: clauses) {
            if (clause instanceof TextPatternOr) {
                // Flatten nested ORs
                this.clauses.addAll(((TextPatternOr) clause).clauses);
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
        if (chResults.size() == 1)
            return chResults.get(0);
        return new BLSpanOrQuery(chResults.toArray(new BLSpanQuery[] {}));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternOr that = (TextPatternOr) o;
        return Objects.equals(clauses, that.clauses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clauses);
    }

    @Override
    public String toString() {
        return "OR(" + clausesToString(clauses) + ")";
    }

    public List<TextPattern> getClauses() {
        return clauses;
    }

    @Override
    public boolean isBracketQuery() {
        return clauses.stream().allMatch(TextPattern::isBracketQuery);
    }

    @Override
    public boolean isRelationsQuery() {
        return clauses.stream().anyMatch(TextPattern::isRelationsQuery);
    }
}
