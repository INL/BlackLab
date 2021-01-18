package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A query matching nothing.
 */
public class SpanQueryNoHits extends BLSpanQuery {

    private String luceneField;

    public SpanQueryNoHits(QueryInfo queryInfo, String luceneField) {
        super(queryInfo);
        this.luceneField = luceneField;
    }

    @Override
    public BLSpanWeight createWeight(final IndexSearcher searcher, boolean needsScores) throws IOException {
        return new BLSpanWeight(this, searcher, null) {
            @Override
            public void extractTerms(Set<Term> terms) {
                // No terms
            }

            @Override
            public void extractTermContexts(Map<Term, TermContext> contexts) {
                // No terms
            }

            @Override
            public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
                return null; // no hits
            }
        };
    }

    @Override
    public String toString(String field) {
        return "NOTOKENS()";
    }

    @Override
    public String getRealField() {
        return luceneField;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((luceneField == null) ? 0 : luceneField.hashCode());
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
        SpanQueryNoHits other = (SpanQueryNoHits) obj;
        if (luceneField == null) {
            if (other.luceneField != null)
                return false;
        } else if (!luceneField.equals(other.luceneField))
            return false;
        return true;
    }

    @Override
    public BLSpanQuery inverted() {
        return new SpanQueryAnyToken(queryInfo, 1, 1, luceneField);
    }

    @Override
    public boolean hitsAllSameLength() {
        return true;
    }

    @Override
    public int hitsLengthMin() {
        return 1;
    }

    @Override
    public int hitsLengthMax() {
        return 1;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return hitsAllSameLength();
    }

    @Override
    public boolean hitsStartPointSorted() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return true;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return true;
    }

    @Override
    public boolean hitsAreUnique() {
        return true;
    }

    @Override
    public boolean canMakeNfa() {
        return false;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0; // no hits, no cost
    }

    @Override
    public int forwardMatchingCost() {
        return 0; // no hits, no cost
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        return this;
    }
}
