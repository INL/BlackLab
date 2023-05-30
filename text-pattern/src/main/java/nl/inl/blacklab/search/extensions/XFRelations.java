package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryRelationSpanAdjust;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFRelations implements ExtensionFunctionClass {

    /** Relation type to prepend if argument does not contain substring "::" */
    private static final String DEFAULT_RELATION_TYPE = "dep"; // could be made configurable if needed

    /** Previous version of rel that takes a relation type ans spanMode, default "target" */
    private static BLSpanQuery relt(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        String relationType = optPrependDefaultType((String) args.get(0));
        RelationInfo.SpanMode spanMode = RelationInfo.SpanMode.fromCode((String)args.get(1));
        String field = context.withRelationAnnotation().luceneField();
        return new SpanQueryRelations(queryInfo, field, relationType, (Map<String, String> )null,
                SpanQueryRelations.Direction.BOTH_DIRECTIONS, spanMode);
    }

    /**
     * Find relations matching type and target.
     *
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
        String field = context.withRelationAnnotation().luceneField();
        if (matchTarget != null) {
            // Ensure relation matches given target
            BLSpanQuery rel = new SpanQueryRelations(queryInfo, field, relationType, (Map<String, String>) null,
                    SpanQueryRelations.Direction.BOTH_DIRECTIONS, RelationInfo.SpanMode.TARGET);
            rel = new SpanQueryRelationSpanAdjust(new SpanQueryAnd(List.of(rel, matchTarget)), spanMode);
            return rel;
        } else {
            return new SpanQueryRelations(queryInfo, field, relationType, (Map<String, String>) null,
                    SpanQueryRelations.Direction.BOTH_DIRECTIONS, spanMode);
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
            relationType = DEFAULT_RELATION_TYPE + "::" + relationType;
        return relationType;
    }

    private static BLSpanQuery rspan(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        BLSpanQuery relations = (BLSpanQuery) args.get(0);
        RelationInfo.SpanMode mode = RelationInfo.SpanMode.fromCode((String)args.get(1));
        return new SpanQueryRelationSpanAdjust(relations, mode);
    }

    private static BLSpanQuery rmatch(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        if (args.isEmpty())
            throw new IllegalArgumentException("rmatch() requires at least one argument");

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
        return new SpanQueryAnd(clauses); // TODO: ensure clauses don't match the same relation twice!
    }

    public void register() {
        /** Resolve second clause using forward index and the first clause using regular reverse index */
        QueryExtensions.register("relt", XFRelations::relt, QueryExtensions.ARGS_SS, List.of(".*", "target"));
        QueryExtensions.register("rel", XFRelations::rel, QueryExtensions.ARGS_SQS, Arrays.asList(".*",
                QueryExtensions.VALUE_QUERY_ANY_NGRAM_, "source"));
        QueryExtensions.register("rmatch", XFRelations::rmatch, QueryExtensions.ARGS_VAR_Q,
                Collections.emptyList());
        QueryExtensions.register("rspan", XFRelations::rspan, QueryExtensions.ARGS_QS, Arrays.asList(null, "full"));
    }

}
