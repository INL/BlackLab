package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureRelationsWithinSpan;
import nl.inl.blacklab.search.lucene.SpanQueryRelationSpanAdjust;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFRelations implements ExtensionFunctionClass {

    public static final String FUNC_REL = "rel";
    public static final String FUNC_RMATCH = "rmatch";
    public static final String FUNC_RSPAN = "rspan";
    public static final String FUNC_RCAPTURE = "rcapture";
    public static final String FUNC_RCAPTURE2 = "rcapture2";

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
        String relationType = (String) args.get(0);
        BLSpanQuery matchTarget = (BLSpanQuery) args.get(1);
        RelationInfo.SpanMode spanMode = RelationInfo.SpanMode.fromCode((String)args.get(2));
        String captureAs = (String)args.get(3);
        SpanQueryRelations.Direction direction = SpanQueryRelations.Direction.fromCode((String)args.get(4));
        return createRelationQuery(queryInfo, context, relationType, matchTarget, direction, captureAs, spanMode, null);
    }

    public static BLSpanQuery createRelationQuery(QueryInfo queryInfo, QueryExecutionContext context, String relationType,
            BLSpanQuery matchTarget, SpanQueryRelations.Direction direction, String captureAs,
            RelationInfo.SpanMode spanMode, String targetField) {

        // Autodetermine capture name if no explicit name given.
        // Discard relation class if specified, keep Unicode letters from relationType, and add unique number
        if (StringUtils.isEmpty(captureAs)) {
            String captureName = RelationUtil.classAndType(relationType)[1].replaceAll("[^\\p{L}]", "");
            if (captureName.isEmpty())
                captureName = FUNC_REL;
            captureAs = context.ensureUniqueCapture(captureName);
        }

        // Make sure relationType has a relation class
        relationType = RelationUtil.optPrependDefaultClass(relationType);

        // Do we need to match a target, or don't we care?
        if (BLSpanQuery.isAnyNGram(matchTarget))
            matchTarget = null;
        String field = context.withRelationAnnotation().luceneField();
        if (matchTarget != null) {
            // Ensure relation matches given target, then adjust to the requested span mode
            BLSpanQuery rel = new SpanQueryRelations(queryInfo, field, relationType, null,
                    direction, RelationInfo.SpanMode.TARGET, captureAs, targetField);
            BLSpanQuery relAndTarget = new SpanQueryAnd(List.of(rel, matchTarget));
            ((SpanQueryAnd)relAndTarget).setRequireUniqueRelations(true); // don't match the same relation twice
            if (spanMode != RelationInfo.SpanMode.TARGET) {
                // Not in the target but the source field. Adjust spans accordingly.
                relAndTarget = new SpanQueryRelationSpanAdjust(relAndTarget, spanMode, context.luceneField());

                // @@@ TODO ensure the correct field is returned (source field)
            }
            return relAndTarget;
        } else {
            // No target to match; we can just return the relation matches with the correct span mode right away
            return new SpanQueryRelations(queryInfo, field, relationType, null,
                    direction, spanMode, captureAs, targetField);
        }
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
        return new SpanQueryRelationSpanAdjust(relations, mode, null);
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
        List<BLSpanQuery> tps = args.stream().map(o -> (BLSpanQuery)o).collect(Collectors.toList());
        return TextPatternRelationMatch.createRelMatchQuery(queryInfo, context, tps);
    }

    /**
     * Capture relations inside a span.
     *
     * Will capture all relations matching the specified type regex as a list
     * under the specified capture name.
     *
     * @param queryInfo
     * @param context
     * @param args function arguments: query, captureAs, relationType
     * @return
     */
    private static BLSpanQuery rcapture(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.isEmpty())
            throw new IllegalArgumentException("rcapture() requires at least a query");
        BLSpanQuery query = (BLSpanQuery)args.get(0);
        String captureAs = context.ensureUniqueCapture((String)args.get(1));
        String relationType = optPrependDefaultType((String) args.get(2));
        String field = context.withRelationAnnotation().luceneField();
        return new SpanQueryCaptureRelationsWithinSpan(queryInfo, field, query, null, captureAs, relationType);
    }

    /**
     * Capture relations inside a captured group.
     *
     * Will capture all relations matching the specified type regex as a list
     * under the specified capture name.
     *
     * @param queryInfo
     * @param context
     * @param args function arguments: query, toCapture, captureAs, relationType
     * @return
     */
    private static BLSpanQuery rcaptureWithinCapture(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.size() < 3)
            throw new IllegalArgumentException("rcapture() requires at least three arguments: query, toCapture, and captureAs");
        BLSpanQuery query = (BLSpanQuery)args.get(0);
        String toCapture = (String)args.get(1);
        String captureAs = context.ensureUniqueCapture((String)args.get(2));
        String relationType = RelationUtil.optPrependDefaultClass((String) args.get(3));
        String field = context.withRelationAnnotation().luceneField();
        return new SpanQueryCaptureRelationsWithinSpan(queryInfo, field, query, toCapture, captureAs, relationType);
    }

    public void register() {
        QueryExtensions.register(FUNC_REL, XFRelations::rel, QueryExtensions.ARGS_SQSSS,
                Arrays.asList(".*", QueryExtensions.VALUE_QUERY_ANY_NGRAM, "source", "", "both"),
                true);
        QueryExtensions.register(FUNC_RMATCH, XFRelations::rmatch, QueryExtensions.ARGS_VAR_Q,
                List.of(QueryExtensions.VALUE_QUERY_ANY_NGRAM));
        QueryExtensions.register(FUNC_RSPAN, XFRelations::rspan, QueryExtensions.ARGS_QS,
                Arrays.asList(null, "full"));
        QueryExtensions.register(FUNC_RCAPTURE, XFRelations::rcapture, QueryExtensions.ARGS_QSS,
                Arrays.asList(null, "rels", ".*"), true);
        QueryExtensions.register(FUNC_RCAPTURE2, XFRelations::rcaptureWithinCapture, QueryExtensions.ARGS_QSSS,
                Arrays.asList(null, null, "rels", ".*"), true);
    }

}
