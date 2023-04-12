package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 *
 * Returns relation spans matching the given type and (optionally) attributes.
 * <p>
 * This version works with the integrated index and the new _relation annotation.
 * <p>
 * For example, SpanQueryTags("ne") will give us spans for all the {@code <ne>}
 * elements in the document.
 */
public class SpanQueryRelations extends BLSpanQuery implements TagQuery {

    public enum Filter {
        // Only return root relations (relations without a source)
        ROOT("root"),

        // Only return relations where target occurs after source
        FORWARD("forward"),

        // Only return relations where target occurs before source
        BACKWARD("backward"),

        // Return any relation
        BOTH_DIRECTIONS("both");

        private String code;

        Filter(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public static Filter fromCode(String code) {
            for (Filter filter : values()) {
                if (filter.getCode().equals(code)) {
                    return filter;
                }
            }
            throw new IllegalArgumentException("Unknown relation filter: " + code);
        }
    }

    private BLSpanQuery clause;

    private String relationType;

    private String baseFieldName;

    private String relationFieldName;

    private Filter filter;

    public SpanQueryRelations(QueryInfo queryInfo, String relationFieldName, String relationType, Map<String, String> attributes, Filter filter) {
        super(queryInfo);

        // Construct the clause from the field, relation type and attributes
        String regexp = AnnotatedFieldNameUtil.relationSearchRegex(relationType, attributes);
        RegexpQuery regexpQuery = new RegexpQuery(new Term(relationFieldName, regexp));
        BLSpanQuery clause = new BLSpanMultiTermQueryWrapper<>(queryInfo, regexpQuery);

        init(relationFieldName, relationType, clause, filter);
    }

    public SpanQueryRelations(QueryInfo queryInfo, String relationFieldName, String relationType, BLSpanQuery clause, Filter filter) {
        super(queryInfo);
        init(relationFieldName, relationType, clause, filter);
    }

    private void init(String relationFieldName, String relationType, BLSpanQuery clause, Filter filter) {
        this.relationType = relationType;
        baseFieldName = AnnotatedFieldNameUtil.getBaseName(relationFieldName);
        this.relationFieldName = relationFieldName;
        this.clause = clause;
        this.filter = filter;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clause.rewrite(reader);
        if (rewritten == clause)
            return this;
        return new SpanQueryRelations(queryInfo, relationFieldName, relationType, rewritten, filter);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            clause.visit(visitor.getSubVisitor(Occur.MUST, this));
        }
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clause.createWeight(searcher, scoreMode, boost);
        return new Weight(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    class Weight extends BLSpanWeight {

        final BLSpanWeight weight;

        public Weight(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryRelations.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        @Deprecated
        public void extractTerms(Set<Term> terms) {
            weight.extractTerms(terms);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            FieldInfo fieldInfo = context.reader().getFieldInfos().fieldInfo(relationFieldName);
            boolean primaryIndicator = BlackLabIndexIntegrated.isForwardIndexField(fieldInfo);
            return new SpansRelations(spans, primaryIndicator, filter);
        }

    }

    @Override
    public String toString(String field) {
        // TODO: we should really change this to REL but it breaks tests for the integrated index...
        return "TAGS(" + relationType + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryRelations that = (SpanQueryRelations) o;
        return clause.equals(that.clause) && relationType.equals(that.relationType) && baseFieldName.equals(that.baseFieldName)
                && relationFieldName.equals(that.relationFieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, relationType, baseFieldName, relationFieldName);
    }

    /**
     * Returns the name of the search field. In the case of a annotated field, the
     * clauses may actually query different annotations of the same annotated field
     * (e.g. "description" and "description__pos"). That's why only the prefix is
     * returned.
     *
     * @return name of the search field
     */
    @Override
    public String getField() {
        return baseFieldName;
    }

    @Override
    public String getRealField() {
        return relationFieldName;
    }

    public String getElementName() {
        return AnnotatedFieldNameUtil.inlineTagNameFromRelationType(relationType);
    }

    public String getRelationType() {
        return relationType;
    }

    @Override
    public boolean hitsAllSameLength() {
        return false;
    }

    @Override
    public int hitsLengthMin() {
        return 0;
    }

    @Override
    public int hitsLengthMax() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return false;
    }

    @Override
    public boolean hitsStartPointSorted() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return false;
    }

    @Override
    public boolean hitsAreUnique() {
        return hitsHaveUniqueStart() || hitsHaveUniqueEnd();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clause.reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clause.forwardMatchingCost();
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        clause.setQueryInfo(queryInfo);
    }
}
