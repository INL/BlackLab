package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.Collections;
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

    enum ArgType {
        QUERY,
        STRING
    }

    private static class FuncInfo {
        FunctionType func;

        List<ArgType> argTypes;

        List<Object> defaultValues;

        public FuncInfo(FunctionType func, List<ArgType> argTypes, List<Object> defaultValues) {
            this.func = func;
            this.argTypes = argTypes;
            this.defaultValues = defaultValues;
        }
    }

    /** Registry of extension functions by name */
    private static Map<String, FuncInfo> functions = new HashMap<>();

    static {
        // Add some debug functions to the registry
        register("_FI1", _FI1, List.of(ArgType.QUERY, ArgType.QUERY));
        register("_FI2", _FI2, List.of(ArgType.QUERY, ArgType.QUERY));
    }

    /**
     * Add a query function to the registry.
     *
     * @param func query extension function
     * @param argTypes argument types
     */
    public static void register(String name, FunctionType func, List<ArgType> argTypes) {
        functions.put(name, new FuncInfo(func, argTypes, Collections.emptyList()));
    }

    /**
     * Add a query function to the registry.
     *
     * @param func query extension function
     * @param argTypes argument types
     * @param defaultValues default values for arguments
     */
    public static void register(String name, FunctionType func, List<ArgType> argTypes, List<Object> defaultValues) {
        functions.put(name, new FuncInfo(func, argTypes, defaultValues));
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
     * Make sure we recognize a string arg as a string and not a query.
     *
     * @param name name of the function
     * @param args arguments
     * @return arguments with the correct type
     */
    public static List<?> preprocessArgs(String name, List<?> args) {
        FuncInfo funcInfo = functions.get(name);
        if (funcInfo == null)
            throw new UnsupportedOperationException("Unknown function: " + name);

        // Add any default argument values
        List<Object> newArgs = new ArrayList<>(args);
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            switch (funcInfo.argTypes.get(i)) {
            case STRING:
                if (arg instanceof TextPatternTerm) {
                    // Interpret as regular string, not as a query
                    newArgs.set(i, ((TextPatternTerm) arg).getValue());
                }
                break;
            }
        }
        return newArgs;
    }

    /**
     * Apply a query extension function.
     * @param name name of the function
     * @param context query execution context
     * @param args arguments
     * @return the query returned by the function
     */
    public static BLSpanQuery apply(String name, QueryExecutionContext context, List<Object> args) {
        FuncInfo funcInfo = functions.get(name);
        if (funcInfo == null)
            throw new UnsupportedOperationException("Unknown function: " + name);

        // Add any default argument values
        List<Object> newArgs = new ArrayList<>(args);
        for (int i = 0; i < funcInfo.argTypes.size(); i++) {
            // Fill in default value for argument if missing
            if (i >= args.size()) {
                // Missing argument.
                if (i >= funcInfo.defaultValues.size())
                    throw new IllegalArgumentException("Missing argument " + (i + 1) + " for function " + name + " (no default value available)");
                newArgs.add(funcInfo.defaultValues.get(i));
            } else if (newArgs.get(i) == null) {
                // Explicitly set to empty (_)
                newArgs.set(i, funcInfo.defaultValues.get(i));
            }

            // Check argument type
            ArgType expectedType = funcInfo.argTypes.get(i);
            boolean wrongType = true;
            switch (expectedType) {
            case QUERY:
                wrongType = !(newArgs.get(i) instanceof BLSpanQuery);
                break;
            case STRING:
                wrongType = !(newArgs.get(i) instanceof String);
            }
            if (wrongType)
                throw new IllegalArgumentException("Argument " + (i + 1) + " for function " + name + " has the wrong type: expected " + expectedType
                        + ", got " + expectedType);
        }

        if (newArgs.size() != funcInfo.argTypes.size())
            throw new IllegalArgumentException("Wrong number of arguments for query function " + name + ": expected " + funcInfo.argTypes.size() + ", got " + newArgs.size());
        return funcInfo.func.apply(context, args);
    }

}
