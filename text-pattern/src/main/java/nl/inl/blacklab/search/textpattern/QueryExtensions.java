package nl.inl.blacklab.search.textpattern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;

/**
 * Manages extension functions that can be used in queries.
 */
public class QueryExtensions {

    /**
     * A function that can be used as a sequence part in CQL.
     *
     * Such a function takes a number of arguments and returns a BLSpanQuery.
     */
    interface FunctionType {
        BLSpanQuery apply(QueryExecutionContext context, List<Object> args);
    }

    /** Resolve second clause using forward index and the first clause using regular reverse index */
    private static FunctionType _FI1 = (context, args) -> {
        BLSpanQuery a = (BLSpanQuery)args.get(0);
        BLSpanQuery b = (BLSpanQuery)args.get(1);
        ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(b.getField());
        NfaTwoWay nfaTwoWay = b.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
        return new SpanQueryFiSeq(a, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, b, SpanQueryFiSeq.DIR_TO_RIGHT,
                fiAccessor);
    };

    /** Resolve first clause using forward index and the second clause using regular reverse index */
    private static FunctionType _FI2 = (context, args) -> {
        BLSpanQuery a = (BLSpanQuery)args.get(0);
        BLSpanQuery b = (BLSpanQuery)args.get(1);
        ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(a.getField());
        NfaTwoWay nfaTwoWay = a.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
        return new SpanQueryFiSeq(b, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, a, SpanQueryFiSeq.DIR_TO_LEFT,
                fiAccessor);
    };

    /** Registry of extension functions by name */
    private static Map<String, FunctionType> functions = new HashMap<>();

    static {
        // Add some debug functions to the registry
        add("_FI1", _FI1);
        add("_FI2", _FI2);
    }

    /**
     * Add a query function to the registry.
     *
     * @param func query extension function
     */
    public static void add(String name, FunctionType func) {
        functions.put(name, func);
    }

    /**
     * Get a query function from the registry.
     *
     * @param name name of the query function
     * @return the query function, or null if not found
     */
    public static FunctionType get(String name) {
        return functions.get(name);
    }

    /**
     * Check if a query function exists.
     * @param name name of the query function
     * @return true if it exists, false if not
     */
    public static boolean exists(String name) {
        return functions.containsKey(name);
    }

    /**
     * Apply a query extension function.
     * @param name name of the function
     * @param context query execution context
     * @param args arguments
     * @return the query returned by the function
     */
    public static BLSpanQuery apply(String name, QueryExecutionContext context, List<Object> args) {
        FunctionType functionType = QueryExtensions.get(name);
        if (functionType == null)
            throw new UnsupportedOperationException("Unsupported query function: " + name);
        return functionType.apply(context, args);
    }

}
