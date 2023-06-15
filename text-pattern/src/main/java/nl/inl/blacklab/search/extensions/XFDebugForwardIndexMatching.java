package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFDebugForwardIndexMatching implements ExtensionFunctionClass {

    /** Resolve second clause using forward index and the first clause using regular reverse index */
    private static BLSpanQuery _FI1(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        BLSpanQuery a = (BLSpanQuery) args.get(0);
        BLSpanQuery b = (BLSpanQuery) args.get(1);
        ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(b.getField());
        NfaTwoWay nfaTwoWay = b.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
        return new SpanQueryFiSeq(a, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, b, SpanQueryFiSeq.DIR_TO_RIGHT,
                fiAccessor);
    }

    /** Resolve first clause using forward index and the second clause using regular reverse index */
    private static BLSpanQuery _FI2(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        BLSpanQuery a = (BLSpanQuery) args.get(0);
        BLSpanQuery b = (BLSpanQuery) args.get(1);
        ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(a.getField());
        NfaTwoWay nfaTwoWay = a.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
        return new SpanQueryFiSeq(b, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, a, SpanQueryFiSeq.DIR_TO_LEFT,
                fiAccessor);
    }

    public void register() {
        QueryExtensions.register("_FI1", XFDebugForwardIndexMatching::_FI1, QueryExtensions.ARGS_QQ);
        QueryExtensions.register("_FI2", XFDebugForwardIndexMatching::_FI2, QueryExtensions.ARGS_QQ);
    }

}
