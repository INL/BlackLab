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
 * Returns spans corresponding to a certain element (tag) type.
 * <p>
 * This version works with the integrated index and the new _relation annotation.
 * <p>
 * For example, SpanQueryTags("ne") will give us spans for all the {@code <ne>}
 * elements in the document.
 */
public class SpanQueryTagsIntegrated extends BLSpanQuery implements TagQuery {

    private BLSpanQuery clause;

    private String tagName;

    private String baseFieldName;

    private String relationFieldName;

    public SpanQueryTagsIntegrated(QueryInfo queryInfo, String relationFieldName, String tagName, Map<String, String> attributes) {
        super(queryInfo);

        // Construct the clause from the field, tag name and attributes
        String relationType = AnnotatedFieldNameUtil.spanRelationType(tagName);
        String regexp = AnnotatedFieldNameUtil.relationSearchRegex(relationType, attributes);
        BLSpanQuery clause = new BLSpanMultiTermQueryWrapper<>(queryInfo, new RegexpQuery(new Term(relationFieldName, regexp)));

        init(relationFieldName, tagName, clause);
    }

    public SpanQueryTagsIntegrated(QueryInfo queryInfo, String relationFieldName, String tagName, BLSpanQuery clause) {
        super(queryInfo);
        init(relationFieldName, tagName, clause);
    }

    private void init(String relationFieldName, String tagName, BLSpanQuery clause) {
        this.tagName = tagName;
        baseFieldName = AnnotatedFieldNameUtil.getBaseName(relationFieldName);
        this.relationFieldName = relationFieldName;
        this.clause = clause;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clause.rewrite(reader);
        if (rewritten == clause)
            return this;
        return new SpanQueryTagsIntegrated(queryInfo, relationFieldName, tagName, rewritten);
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
            super(SpanQueryTagsIntegrated.this, searcher, terms, boost);
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
            return new SpansTagsIntegrated(spans, primaryIndicator);
        }

    }

    @Override
    public String toString(String field) {
        return "TAGS(" + tagName + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryTagsIntegrated that = (SpanQueryTagsIntegrated) o;
        return clause.equals(that.clause) && tagName.equals(that.tagName) && baseFieldName.equals(that.baseFieldName)
                && relationFieldName.equals(that.relationFieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, tagName, baseFieldName, relationFieldName);
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
        return tagName;
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
