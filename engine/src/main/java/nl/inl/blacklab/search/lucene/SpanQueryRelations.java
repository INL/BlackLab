package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private Map<String, String> attributes;

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, Direction direction,
            RelationInfo.SpanMode spanMode) {
        if (!clause.hitsStartPointSorted())
            return clause;
        boolean sorted;
        switch (spanMode) {
        case SOURCE:
            // All relations are indexed at the source.
            // Root relations don't have a source and are indexed at the target, therefore also sorted.
            sorted = true;
            break;
        case FULL_SPAN:
            // All relations are indexed at the source.
            // Only forward relations will be sorted.
            // Root relations only have target and are indexed there, therefore also sorted.
            sorted = direction == Direction.FORWARD || direction == Direction.ROOT;
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
        return new SpanQueryRelations(queryInfo, relationFieldName, relationType, attributes, clause, direction, mode, captureAs);
    }

    public boolean isTagQuery() {
        String relationClass = RelationUtil.classAndType(relationType)[0];
        return relationClass.equals(RelationUtil.RELATION_CLASS_INLINE_TAG);
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

    private String captureAs;

    public SpanQueryRelations(QueryInfo queryInfo, String relationFieldName, String relationType,
            Map<String, String> attributes, Direction direction, RelationInfo.SpanMode spanMode, String captureAs) {
        super(queryInfo);

        if (StringUtils.isEmpty(relationFieldName))
            throw new IllegalArgumentException("relationFieldName must be non-empty");
        if (spanMode == RelationInfo.SpanMode.ALL_SPANS)
            throw new IllegalArgumentException("ALL_SPANS makes no sense for SpanQueryRelations");

        // Construct the clause from the field, relation type and attributes
        String regexp = RelationUtil.searchRegex(queryInfo.index(), relationType, attributes);
        RegexpQuery regexpQuery = new RegexpQuery(new Term(relationFieldName, regexp), RegExp.COMPLEMENT);
        BLSpanQuery clause = new BLSpanMultiTermQueryWrapper<>(queryInfo, regexpQuery);

        init(relationFieldName, relationType, attributes, clause, direction, spanMode, captureAs);
    }

    public SpanQueryRelations(QueryInfo queryInfo, String relationFieldName, String relationType,
            Map<String, String> attributes, BLSpanQuery clause, Direction direction, RelationInfo.SpanMode spanMode,
            String captureAs) {
        super(queryInfo);
        init(relationFieldName, relationType, attributes, clause, direction, spanMode, captureAs);
    }

    private void init(String relationFieldName, String relationType, Map<String, String> attributes, BLSpanQuery clause, Direction direction,
            RelationInfo.SpanMode spanMode, String captureAs) {
        this.relationFieldName = relationFieldName;
        baseFieldName = AnnotatedFieldNameUtil.getBaseName(relationFieldName);
        this.relationType = relationType;
        this.attributes = new HashMap<>(attributes == null ? Collections.emptyMap() : attributes);
        this.clause = clause;
        this.direction = direction;
        this.spanMode = spanMode;
        this.captureAs = captureAs == null ? "" : captureAs;
        this.guarantees = createGuarantees(clause.guarantees(), direction, spanMode);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clause.rewrite(reader);
        if (rewritten == clause)
            return this;
        return new SpanQueryRelations(queryInfo, relationFieldName, relationType, attributes, rewritten, direction,
                spanMode, captureAs);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            clause.visit(visitor.getSubVisitor(Occur.MUST, this));
        }
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
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
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
        }

        @Override
        public SpansRelations getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            FieldInfo fieldInfo = context.reader().getFieldInfos().fieldInfo(relationFieldName);
            boolean primaryIndicator = BlackLabIndexIntegrated.isForwardIndexField(fieldInfo);
            return new SpansRelations(relationType, spans, primaryIndicator, direction, spanMode, captureAs);
        }

    }

    @Override
    public String toString(String field) {
        String inlineTagsPrefix = RelationUtil.RELATION_CLASS_INLINE_TAG + RelationUtil.RELATION_CLASS_TYPE_SEPARATOR;
        String optCaptureAs = captureAs.isEmpty() ? "" : ", cap:" + captureAs;
        if (relationType.startsWith(inlineTagsPrefix)) {
            String type = relationType.substring(inlineTagsPrefix.length());
            String optAttr = attributes != null && !attributes.isEmpty() ? ", " + attributes : "";
            return "TAGS(" + type + optAttr + optCaptureAs + ")";
        } else {
            // relations query
            String optDirection = direction != Direction.BOTH_DIRECTIONS ? ", dir:" + direction : "";
            return "REL(" + relationType + ", " + spanMode + optDirection + optCaptureAs + ")";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryRelations that = (SpanQueryRelations) o;
        return Objects.equals(attributes, that.attributes) && Objects.equals(clause, that.clause)
                && Objects.equals(relationType, that.relationType) && Objects.equals(baseFieldName,
                that.baseFieldName) && Objects.equals(relationFieldName, that.relationFieldName)
                && direction == that.direction && spanMode == that.spanMode && Objects.equals(captureAs,
                that.captureAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes, clause, relationType, baseFieldName, relationFieldName, direction, spanMode,
                captureAs);
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
