package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.SpanQueryRelationSpanAdjust;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFRelations implements ExtensionFunctionClass {

    private static BLSpanQuery rel(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        String relationType = (String) args.get(0);
        MatchInfo.SpanMode spanMode = MatchInfo.SpanMode.fromCode((String)args.get(1));
        SpanQueryRelations.Direction direction = SpanQueryRelations.Direction.fromCode((String) args.get(2));
        String field = context.withRelationAnnotation().luceneField();
        return new SpanQueryRelations(queryInfo, field, relationType, (Map<String, String> )null, direction,
                spanMode);
    }

    private static BLSpanQuery rspan(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        BLSpanQuery relations = (BLSpanQuery) args.get(0);
        MatchInfo.SpanMode mode = MatchInfo.SpanMode.fromCode((String)args.get(1));
        return new SpanQueryRelationSpanAdjust(relations, mode);
    }

    public void register() {
        /** Resolve second clause using forward index and the first clause using regular reverse index */
        QueryExtensions.register("rel", XFRelations::rel, QueryExtensions.ARGS_SSS, List.of(".*", "target", "both"));
        QueryExtensions.register("rspan", XFRelations::rspan, QueryExtensions.ARGS_QS, Arrays.asList(null, "full"));
    }

}
