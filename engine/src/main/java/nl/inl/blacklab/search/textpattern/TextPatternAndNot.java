package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAndNot;

/**
 * AND NOT operation (deprecated).
 * 
 * @deprecated use TextPatternAnd with TextPatternNot instead
 */
@Deprecated
public class TextPatternAndNot extends TextPattern {

    protected final List<TextPattern> include = new ArrayList<>();

    protected final List<TextPattern> exclude = new ArrayList<>();

    public TextPatternAndNot(List<TextPattern> includeClauses, List<TextPattern> excludeClauses) {
        assert excludeClauses != null && !excludeClauses.isEmpty();
        include.addAll(includeClauses);
        exclude.addAll(excludeClauses);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<BLSpanQuery> chResults = new ArrayList<>(include.size());
        for (TextPattern cl : include) {
            chResults.add(cl.translate(context));
        }
        List<BLSpanQuery> chResultsNot = new ArrayList<>(exclude.size());
        for (TextPattern cl : exclude) {
            chResultsNot.add(cl.translate(context));
        }
        return new SpanQueryAndNot(chResults, chResultsNot);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAndNot) {
            return include.equals(((TextPatternAndNot) obj).include) &&
                    exclude.equals(((TextPatternAndNot) obj).exclude);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return include.hashCode() + exclude.hashCode();
    }

    @Override
    public String toString() {
        return "ANDNOT([" + clausesToString(include) +
                "], [" + clausesToString(exclude) + "])";
    }

    public List<TextPattern> getInclude() {
        return include;
    }

    public List<TextPattern> getExclude() {
        return exclude;
    }
}
