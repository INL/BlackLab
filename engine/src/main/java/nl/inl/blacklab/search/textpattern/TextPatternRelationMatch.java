package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

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

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        TextPattern parentNoDefVal = TextPatternDefaultValue.replaceWithAnyToken(parent);
        BLSpanQuery qParent = parentNoDefVal.translate(context.withDocVersion(sourceVersion));
        List<BLSpanQuery> queries = new ArrayList<>();
        queries.add(qParent);
        for (TextPattern child: children) {
            queries.add(child.translate(context));
        }
        return XFRelations.createRelMatchQuery(context.queryInfo(), context, queries);
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
