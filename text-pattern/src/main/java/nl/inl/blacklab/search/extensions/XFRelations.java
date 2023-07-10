package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsWithinSpan;
import nl.inl.blacklab.search.lucene.SpanQueryRelationSpanAdjust;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFRelations implements ExtensionFunctionClass {

    /** Relation type to prepend if argument does not contain substring "::" */
    private static final String DEFAULT_RELATION_TYPE = "dep"; // could be made configurable if needed

    /**
     * Find relations matching type and target.
     * <p>
     * You can also set spanMode (defaults to "source").
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: relation type, target, spanMode
     * @return relations query
     */
    private static BLSpanQuery rel(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        String relationType = optPrependDefaultType((String) args.get(0));
        BLSpanQuery matchTarget = (BLSpanQuery) args.get(1);
        if (isAnyNGram(matchTarget))
            matchTarget = null;
        RelationInfo.SpanMode spanMode = RelationInfo.SpanMode.fromCode((String)args.get(2));
        SpanQueryRelations.Direction direction = SpanQueryRelations.Direction.fromCode((String)args.get(3));
        String field = context.withRelationAnnotation().luceneField();
        if (matchTarget != null) {
            // Ensure relation matches given target
            BLSpanQuery rel = new SpanQueryRelations(queryInfo, field, relationType, (Map<String, String>) null,
                    direction, RelationInfo.SpanMode.TARGET);
            rel = new SpanQueryAnd(List.of(rel, matchTarget));
            ((SpanQueryAnd)rel).setRequireUniqueRelations(true);
            if (spanMode != RelationInfo.SpanMode.TARGET)
                rel = new SpanQueryRelationSpanAdjust(rel, spanMode);
            return rel;
        } else {
            return new SpanQueryRelations(queryInfo, field, relationType, (Map<String, String>) null,
                    direction, spanMode);
        }
    }

    private static boolean isAnyNGram(BLSpanQuery matchTarget) {
        boolean isAnyNGram = false;
        if (matchTarget instanceof SpanQueryAnyToken) {
            SpanQueryAnyToken any = (SpanQueryAnyToken) matchTarget;
            if (any.getMin() == 0 && any.getMax() == BLSpanQuery.MAX_UNLIMITED) {
                // No restrictions on target.
                isAnyNGram = true;
            }
        }
        return isAnyNGram;
    }

    private static String optPrependDefaultType(String relationType) {
        if (!relationType.contains("::"))
            relationType = DEFAULT_RELATION_TYPE + "::(" + relationType + ")";
        return relationType;
    }

    /**
     * Change span mode of a query with an active relation.
     * <p>
     * That is, change the spans the query produces to the source or target
     * spans of the active relation, or the full relation span, or to a span
     * covering all matched relations.
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: query, spanMode
     * @return span-adjusted query
     */
    private static BLSpanQuery rspan(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.size() < 2)
            throw new IllegalArgumentException("rmatch() requires a query and a span mode as arguments");
        BLSpanQuery relations = (BLSpanQuery) args.get(0);
        RelationInfo.SpanMode mode = RelationInfo.SpanMode.fromCode((String)args.get(1));
        return new SpanQueryRelationSpanAdjust(relations, mode);
    }

    /**
     * Perform an AND operation with the additional requirement that clauses match unique relations.
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: clauses
     * @return AND query with unique relations requirement
     */
    private static BLSpanQuery rmatch(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.isEmpty())
            throw new IllegalArgumentException("rmatch() requires one or more queries as arguments");

        // Filter out "any n-gram" arguments ([]*) because they don't do anything
        List<BLSpanQuery> clauses = args.stream()
                .map(o -> (BLSpanQuery)o)
                .filter(q -> !isAnyNGram(q))    // remove any []* clauses, which don't do anything
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

    private static BLSpanQuery rcapture(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.size() < 3)
            throw new IllegalArgumentException("rcapture() requires at least three arguments: query, toCapture, and captureAs");
        BLSpanQuery query = (BLSpanQuery)args.get(0);
        String toCapture = (String)args.get(1);
        String captureAs = (String)args.get(2);
        String relationType = optPrependDefaultType((String) args.get(3));
        String field = context.withRelationAnnotation().luceneField();
        return new SpanQueryCaptureRelationsWithinSpan(queryInfo, field, query, toCapture, captureAs, relationType);
    }

    public void register() {
        QueryExtensions.register("rel", XFRelations::rel, QueryExtensions.ARGS_SQSS, Arrays.asList(".*",
                QueryExtensions.VALUE_QUERY_ANY_NGRAM, "source", "both"));
        QueryExtensions.register("rmatch", XFRelations::rmatch, QueryExtensions.ARGS_VAR_Q,
                List.of(QueryExtensions.VALUE_QUERY_ANY_NGRAM));
        QueryExtensions.register("rspan", XFRelations::rspan, QueryExtensions.ARGS_QS, Arrays.asList(null, "full"));
        QueryExtensions.register("rcapture", XFRelations::rcapture, QueryExtensions.ARGS_QSSS, Arrays.asList(null, null, null, ".*"));
    }

}
