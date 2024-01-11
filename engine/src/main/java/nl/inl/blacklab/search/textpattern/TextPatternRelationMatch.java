package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Relations operator, matching a source (parent) to one or more targets (children).
 */
public class TextPatternRelationMatch extends TextPattern {

    /** What doocument version to find source in, or null if this is not a parallel corpus. */
    private final String sourceVersion;

    private final TextPattern parent;

    private final List<TextPattern> children;

    public TextPatternRelationMatch(String sourceVersion, TextPattern parent, List<TextPattern> children) {
        this.sourceVersion = sourceVersion;
        this.parent = parent;
        assert children.size() > 0;
        this.children = children;
    }

    public static BLSpanQuery createRelMatchQuery(QueryInfo queryInfo, QueryExecutionContext context,
            List<BLSpanQuery> args) {
        assert !args.isEmpty();
        // Filter out "any n-gram" arguments ([]*) because they don't do anything
        List<BLSpanQuery> clauses = args.stream()
                .filter(q -> !BLSpanQuery.isAnyNGram(q))    // remove any []* clauses, which don't do anything
                .collect(Collectors.toList());

        if (clauses.isEmpty()) {
            // All clauses were []*; return any n-gram query (good luck with that...)
            return SpanQueryAnyToken.anyNGram(queryInfo, context);
        }
        if (clauses.size() == 1) {
            // Nothing to match, just return the clause
            return clauses.get(0);
        }
        SpanQueryAnd spanQueryAnd = new SpanQueryAnd(clauses);
        spanQueryAnd.setRequireUniqueRelations(true); // discard match if relation matched twice
        return spanQueryAnd;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<BLSpanQuery> queries = new ArrayList<>();
        queries.add(TextPatternDefaultValue.replaceWithAnyToken(parent)
                .translate(context.withDocVersion(sourceVersion)));
        for (TextPattern child: children) {
            queries.add(child.translate(context));
        }
        return createRelMatchQuery(context.queryInfo(), context, queries);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternRelationMatch that = (TextPatternRelationMatch) o;
        return Objects.equals(sourceVersion, that.sourceVersion) && Objects.equals(parent, that.parent)
                && Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceVersion, parent, children);
    }

    @Override
    public String toString() {
        return "RMATCH(" + (sourceVersion == null ? "" : sourceVersion + ", ") + parent + ", " + children + ")";
    }

    public String getSourceVersion() {
        return sourceVersion;
    }

    public TextPattern getParent() {
        return parent;
    }

    public List<TextPattern> getChildren() {
        return children;
    }

    @Override
    public boolean isRelationsQuery() {
        return parent.isRelationsQuery() || children.stream().anyMatch(TextPattern::isRelationsQuery);
    }
}
