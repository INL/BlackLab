package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryDefaultValue;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A default value in a query, that will be replaced by something based on context.
 *
 * In a function call, it will be replaced by the default value for that parameter.
 * For an operand of the relation operator --reltype--> it will be replaced with []* ("any span")
 * In any other place, it will produce an error.
 */
public class TextPatternDefaultValue extends TextPattern {

    private static final TextPatternDefaultValue instance = new TextPatternDefaultValue();

    public static TextPatternDefaultValue get() { return instance; }

    private TextPatternDefaultValue() {}

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryDefaultValue(QueryInfo.create(context.index(), context.field()));
    }

    @Override
    public boolean equals(Object obj) {
        return obj == instance;
    }

    @Override
    public int hashCode() {
        return TextPatternDefaultValue.class.hashCode();
    }

    @Override
    public String toString() {
        return "DEFVAL()";
    }
}
