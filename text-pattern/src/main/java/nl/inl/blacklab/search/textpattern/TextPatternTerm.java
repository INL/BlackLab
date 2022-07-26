package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.index.Term;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTerm extends TextPattern {
    protected final String value;

    public String getValue() {
        return value;
    }

    public TextPatternTerm(String value) {
        this.value = value;
    }

    public Term getTerm(String fieldName) {
        return new Term(fieldName, value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new BLSpanTermQuery(QueryInfo.create(context.index(), context.field()), new Term(context.luceneField(),
                context.optDesensitize(optInsensitive(context, value))));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternTerm) {
            return value.equals(((TextPatternTerm) obj).value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "TERM(" + value + ")";
    }
}
