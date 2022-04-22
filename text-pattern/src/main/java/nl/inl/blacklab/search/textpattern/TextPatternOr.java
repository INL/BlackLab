package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanOrQuery;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A TextPattern matching at least one of its child clauses.
 */
public class TextPatternOr extends TextPatternCombiner {

    public TextPatternOr(TextPattern... clauses) {
        super(clauses);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<BLSpanQuery> chResults = new ArrayList<>(clauses.size());
        for (TextPattern cl : clauses) {
            chResults.add(cl.translate(context));
        }
        if (chResults.size() == 1)
            return chResults.get(0);
        return new BLSpanOrQuery(chResults.toArray(new BLSpanQuery[] {}));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternOr) {
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
        return "OR(" + clausesToString(clauses) + ")";
    }
}
