package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A TextPattern matching words that start with the specified prefix.
 */
public class TextPatternPrefix extends TextPatternTerm {
    public TextPatternPrefix(String value) {
        super(value);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws RegexpTooLarge {
        try {
            return new BLSpanMultiTermQueryWrapper<>(QueryInfo.create(context.index(), context.field()), new PrefixQuery(new Term(context.luceneField(),
                    context.optDesensitize(optInsensitive(context, value)))));
        } catch (StackOverflowError e) {
            // If we pass in a prefix expression matching a lot of words,
            // stack overflow may occur inside Lucene's automaton building
            // code and we may end up here.
            throw new RegexpTooLarge();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternPrefix) {
            return super.equals(obj);
        }
        return false;
    }

    // appease PMD
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "PREFIX(" + value + ")";
    }
}
