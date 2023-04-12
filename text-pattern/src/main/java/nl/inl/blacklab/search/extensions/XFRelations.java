package nl.inl.blacklab.search.extensions;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFRelations implements ExtensionFunctionClass {

    private static BLSpanQuery rel(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        String relationType = (String) args.get(0);
        SpanQueryRelations.Filter filter = SpanQueryRelations.Filter.fromCode((String) args.get(1));
        String field = context.withRelationAnnotation().luceneField();
        return new SpanQueryRelations(queryInfo, field, relationType, (Map<String, String> )null, filter);
    }

    public void register() {
        /** Resolve second clause using forward index and the first clause using regular reverse index */
        QueryExtensions.register("rel", XFRelations::rel, QueryExtensions.ARGS_SS);
    }

}
