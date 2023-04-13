package nl.inl.blacklab.search.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

/**
 * Manages extension functions that can be used in queries.
 */
public class QueryExtensions {

    /** A single query as an argument */
    public static final List<ArgType> ARGS_Q = List.of(ArgType.QUERY);

    /** Two queries as an argument */
    public static final List<ArgType> ARGS_QQ = List.of(ArgType.QUERY, ArgType.QUERY);

    /** Three queries as an argument */
    public static final List<ArgType> ARGS_QQQ = List.of(ArgType.QUERY, ArgType.QUERY, ArgType.QUERY);

    /** A query and a string */
    public static final List<ArgType> ARGS_QS = List.of(ArgType.QUERY, ArgType.STRING);

    /** A query, a string and another query */
    public static final List<ArgType> ARGS_QSQ = List.of(ArgType.QUERY, ArgType.STRING, ArgType.QUERY);

    /** Two strings */
    public static final List<ArgType> ARGS_SS = List.of(ArgType.STRING, ArgType.STRING);

    enum ArgType {
        QUERY,
        STRING
    }

    private static class FuncInfo {
        ExtensionFunction func;

        List<ArgType> argTypes;

        List<Object> defaultValues;

        public FuncInfo(ExtensionFunction func, List<ArgType> argTypes, List<Object> defaultValues) {
            this.func = func;
            this.argTypes = argTypes;
            this.defaultValues = defaultValues;
        }
    }

    /** Registry of extension functions by name */
    private static Map<String, FuncInfo> functions = new HashMap<>();

    static {
        register(XFDebugForwardIndexMatching.class); // _FI1(), _FI2()
        register(XFRelations.class);                 // rel(), ...
    }

    public static void register(Class<? extends ExtensionFunctionClass> extClass) {
        try {
            extClass.getConstructor().newInstance().register();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a query function to the registry.
     *
     * @param func query extension function
     * @param argTypes argument types
     */
    public static void register(String name, ExtensionFunction func, List<ArgType> argTypes) {
        functions.put(name, new FuncInfo(func, argTypes, Collections.emptyList()));
    }

    /**
     * Add a query function to the registry.
     *
     * @param func query extension function
     * @param argTypes argument types
     * @param defaultValues default values for arguments
     */
    public static void register(String name, ExtensionFunction func, List<ArgType> argTypes, List<Object> defaultValues) {
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
                if (arg instanceof TextPatternRegex) {
                    // Interpret as regular string, not as a query
                    // kind of a hack, but should work
                    String regex = ((TextPatternTerm) arg).getValue();
                    if (regex.startsWith("^") && regex.endsWith("$")) {
                        // strip off ^ and $
                        regex = regex.substring(1, regex.length() - 1);
                    }
                    newArgs.set(i, regex);
                } else if (arg instanceof TextPatternTerm) {
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
    public static BLSpanQuery apply(String name, QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        FuncInfo funcInfo = functions.get(name);
        if (funcInfo == null)
            throw new UnsupportedOperationException("Unknown function: " + name);

        // Add any default argument values
        List<Object> newArgs = new ArrayList<>(args);
        for (int i = 0; i < funcInfo.argTypes.size(); i++) {
            // Fill in default value for argument if missing
            if (i < funcInfo.defaultValues.size()) {
                if (i >= args.size()) {
                    // Missing argument; use default value
                    newArgs.add(funcInfo.defaultValues.get(i));
                } else if (newArgs.get(i) == null) {
                    // Explicitly set to undefined (_); use default value
                    newArgs.set(i, funcInfo.defaultValues.get(i));
                }
            }
            if (newArgs.get(i) == null) {
                // Still null, so no default value available
                throw new IllegalArgumentException("Missing argument " + (i + 1) + " for function " + name + " (no default value available)");
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
        return funcInfo.func.apply(queryInfo, context, newArgs);
    }

}
