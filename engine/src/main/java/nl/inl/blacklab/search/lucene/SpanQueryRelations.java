package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
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
import org.apache.lucene.util.automaton.RegExp;

import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
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

    /** We assign unique ids to SpanQueryRelations clauses, so the match info names for
     *  multiple relation clauses will be unique. */
    private static AtomicInteger uniqueIdCounter = new AtomicInteger();

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, Direction direction,
            RelationInfo.SpanMode spanMode) {
        if (!clause.hitsStartPointSorted())
            return clause;
        boolean sorted;
        switch (spanMode) {
        case SOURCE:
            if (AnnotationWriter.INDEX_AT_FIRST_POS_IN_DOC) {
                // Forward relations are indexed at the source, we know these will be sorted.
                // Root relations don't have a source and are indexed at the target, therefore also sorted.
                sorted = direction == Direction.FORWARD || direction == Direction.ROOT;
            } else {
                // All relations are indexed at the source.
                // Root relations don't have a source and are indexed at the target, therefore also sorted.
                sorted = true;
            }
            break;
        case FULL_SPAN:
            if (AnnotationWriter.INDEX_AT_FIRST_POS_IN_DOC) {
                // Relations are indexed at source or target, whichever comes first in the document.
                // Because full span covers both source and target, it will always be sorted.
                sorted = true;
            } else {
                // All relations are indexed at the source.
                // Only forward relations will be sorted.
                // Root relations only have target and are indexed there, therefore also sorted.
                sorted = direction == Direction.FORWARD || direction == Direction.ROOT;
            }
            break;
        case TARGET:
        default:
            // Target may be anywhere before or after source, so we don't know if these will be sorted.
            // Exception: root relations only have target and are indexed there, so they will be sorted.
            sorted = direction == Direction.ROOT;
            break;
        }
        return new SpanGuaranteesAdapter(clause) {
            @Override
            public boolean hitsStartPointSorted() {
                return sorted;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return false;
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
                return MAX_UNLIMITED;
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                return false;
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                return false;
            }
        };
    }

    public RelationInfo.SpanMode getSpanMode() {
        return spanMode;
    }

    public BLSpanQuery withSpanMode(RelationInfo.SpanMode mode) {
        if (this.spanMode == mode)
            return this;
        return new SpanQueryRelations(queryInfo, relationFieldName, relationType, clause, direction, mode);
    }

    public enum Direction {
        // Only return root relations (relations without a source)
        ROOT("root"),

        // Only return relations where target occurs after source
        FORWARD("forward"),

        // Only return relations where target occurs before source
        BACKWARD("backward"),

        // Return any relation
        BOTH_DIRECTIONS("both");

        private final String code;

        Direction(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            return getCode();
        }

        public static Direction fromCode(String code) {
            for (Direction dir : values()) {
                if (dir.getCode().equals(code)) {
                    return dir;
                }
            }
            throw new IllegalArgumentException("Unknown relation direction: " + code);
        }
    }

    private BLSpanQuery clause;

    private String relationType;

    private String baseFieldName;

    private String relationFieldName;

    private Direction direction;

    private RelationInfo.SpanMode spanMode;

    /** Ensure unique match info name to avoid collision when matching the same relation type twice */
    private int uniqueId = uniqueIdCounter.getAndIncrement();

    public SpanQueryRelations(QueryInfo queryInfo, String relationFieldName, String relationType,
            Map<String, String> attributes, Direction direction, RelationInfo.SpanMode spanMode) {
        super(queryInfo);

        if (StringUtils.isEmpty(relationFieldName))
            throw new IllegalArgumentException("relationFieldName must be non-empty");
        if (spanMode == RelationInfo.SpanMode.ALL_SPANS)
            throw new IllegalArgumentException("ALL_SPANS makes no sense for SpanQueryRelations");

        // Construct the clause from the field, relation type and attributes
        String regexp = RelationUtil.searchRegex(relationType, attributes);
        RegexpQuery regexpQuery = new RegexpQuery(new Term(relationFieldName, regexp), RegExp.COMPLEMENT);
        BLSpanQuery clause = new BLSpanMultiTermQueryWrapper<>(queryInfo, regexpQuery);
        init(relationFieldName, relationType, clause, direction, spanMode);
    }

    public SpanQueryRelations(QueryInfo queryInfo, String relationFieldName, String relationType, BLSpanQuery clause,
            Direction direction, RelationInfo.SpanMode spanMode) {
        super(queryInfo);
        init(relationFieldName, relationType, clause, direction, spanMode);
    }

    private void init(String relationFieldName, String relationType, BLSpanQuery clause, Direction direction,
            RelationInfo.SpanMode spanMode) {
        this.relationType = relationType;
        baseFieldName = AnnotatedFieldNameUtil.getBaseName(relationFieldName);
        this.relationFieldName = relationFieldName;
        this.clause = clause;
        this.direction = direction;
        this.spanMode = spanMode;
        this.guarantees = createGuarantees(clause.guarantees(), direction, spanMode);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clause.rewrite(reader);
        if (rewritten == clause)
            return this;
        return new SpanQueryRelations(queryInfo, relationFieldName, relationType, rewritten, direction, spanMode);
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
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight.isCacheable(ctx);
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
            return new SpansRelations(relationType, spans, primaryIndicator, direction, spanMode, uniqueId);
        }

    }

    @Override
    public String toString(String field) {
        String inlineTagsPrefix = RelationUtil.RELATION_CLASS_INLINE_TAG + RelationUtil.RELATION_CLASS_TYPE_SEPARATOR;
        if (relationType.startsWith(inlineTagsPrefix))
            return "TAGS(" + relationType + ")";
        else {
            // relations query
            return "REL(" + relationType + ", " + spanMode + (direction != Direction.BOTH_DIRECTIONS ? ", " + direction : "") + ")";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryRelations that = (SpanQueryRelations) o;
        return Objects.equals(clause, that.clause) && Objects.equals(
                relationType, that.relationType) && Objects.equals(baseFieldName, that.baseFieldName)
                && Objects.equals(relationFieldName, that.relationFieldName) && direction == that.direction
                && spanMode == that.spanMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clause, relationType, baseFieldName, relationFieldName, direction, spanMode);
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
        return RelationUtil.classAndType(relationType)[1];
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
