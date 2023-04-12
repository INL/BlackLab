package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.QueryExtensions;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern that applies a function to a list of patterns to produce a new pattern.
 *
 * Right now, this is used for testing purposes, e.g. to experiment with certain optimizations,
 * specifically forward index matching.
 */
public class TextPatternQueryFunction extends TextPattern {

    private final String name;

    private final List<?> args;

    public static TextPatternQueryFunction create(String name, List<TextPattern> args) {
        return new TextPatternQueryFunction(name, args);
    }

    public TextPatternQueryFunction(String name, List<?> args) {
        if (!QueryExtensions.exists(name))
            throw new UnsupportedOperationException("Supported query function: _FI1, _FI2");
        this.name = name;

        // Make sure string arguments are recognized as such (and not seen as a query)
        this.args = QueryExtensions.preprocessArgs(name, args);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<Object> translated = new ArrayList<>();
        for (Object arg: args) {
            if (arg instanceof TextPattern) {
                // Translate any TextPattern arguments to BLSpanQuery
                translated.add(((TextPattern) arg).translate(context));
            } else {
                // Just copy other argument types
                translated.add(arg);
            }
        }

        QueryInfo queryInfo = QueryInfo.create(context.index(), context.field());
        return QueryExtensions.apply(name, queryInfo, context, translated);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((args == null) ? 0 : args.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextPatternQueryFunction other = (TextPatternQueryFunction) obj;
        if (args == null) {
            if (other.args != null)
                return false;
        } else if (!args.equals(other.args))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "QFUNC(" + name + ", " + StringUtils.join(args, ", ") + ")";
    }
}
