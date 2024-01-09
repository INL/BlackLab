package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Search part of the query in another version of the document.
 *
 * Used for parallel corpora, where we want to relate different versions
 * of the document to one another, e.g. different languages or time
 * periods.
 */
public class TextPatternDocVersion extends TextPattern {

    final String version;

    final TextPattern clause;

    public TextPatternDocVersion(String version, TextPattern clause) {
        this.version = version;
        this.clause = clause;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return clause.translate(context.withVersion(version));
    }

    @Override
    public String toString() {
        return "DOCVER(" + version + ", " + clause + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternDocVersion that = (TextPatternDocVersion) o;
        return Objects.equals(version, that.version) && Objects.equals(clause, that.clause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, clause);
    }

    public TextPattern getClause() {
        return clause;
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
