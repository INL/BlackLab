package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A (variable-size) list of captured relations, e.g. all relations in a sentence.
 */
public class RelationListInfo extends MatchInfo {

    public static RelationListInfo create(List<RelationInfo> relations, String overriddenField) {
        return new RelationListInfo(relations, overriddenField);
    }

    List<RelationInfo> relations;

    private final Integer spanStart;

    private final Integer spanEnd;

    private RelationListInfo(List<RelationInfo> relations, String overriddenField) {
        super(overriddenField);
        this.relations = new ArrayList<>(relations);
        spanStart = relations.stream().map(RelationInfo::getSpanStart).min(Integer::compare).orElse(-1);
        spanEnd = relations.stream().map(RelationInfo::getSpanEnd).max(Integer::compare).orElse(-1);
    }

    public int getSpanStart() {
        return spanStart;
    }

    public int getSpanEnd() {
        return spanEnd;
    }

    @Override
    public Type getType() {
        return Type.LIST_OF_RELATIONS;
    }

    @Override
    public String toString(String defaultField) {
        return "listrel(" + relations.stream().map(r -> r.toString(defaultField)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof RelationListInfo)
            return compareTo((RelationListInfo) o);
        return super.compareTo(o);
    }

    public int compareTo(RelationListInfo o) {
        // compare the items in the list of relations
        int n = Integer.compare(relations.size(), o.relations.size());
        if (n != 0)
            return n;
        for (int i = 0; i < relations.size(); i++) {
            n = relations.get(i).compareTo(o.relations.get(i));
            if (n != 0)
                return n;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationListInfo that = (RelationListInfo) o;
        return Objects.equals(relations, that.relations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relations);
    }

    public Collection<RelationInfo> getRelations() {
        return Collections.unmodifiableCollection(relations);
    }
}
