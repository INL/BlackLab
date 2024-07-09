package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Returns either the leading edge or trailing edge of the specified query.
 *
 * E.g. for left-to-right languages, the leading edge is the left edge
 * and the trailing edge is the right edge.
 *
 * Note that the results of this query are zero-length spans.
 */
public class SpanQueryDefaultValue extends BLSpanQuery {

    private final String luceneField;

    public SpanQueryDefaultValue(QueryInfo queryInfo, String luceneField) {
        super(queryInfo);
        this.guarantees = SpanGuarantees.NONE;
        this.luceneField = luceneField;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        return SpanQueryAnyToken.anyNGram(queryInfo, luceneField);
        //throw new BlackLabRuntimeException("SpanQueryDefaultValue must not occur here (should have been replaced)");
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        throw new BlackLabRuntimeException("SpanQueryDefaultValue must not occur here (should have been replaced)");
    }

    @Override
    public String toString(String field) {
        return "DEFVAL()";
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0;
    }

    @Override
    public int forwardMatchingCost() {
        return 0;
    }

    @Override
    public String getRealField() {
        return queryInfo.field().mainAnnotation().mainSensitivity().luceneField();
    }

    @Override
    public int hashCode() {
        return SpanQueryDefaultValue.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SpanQueryDefaultValue;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        // nothing to do
    }
}
