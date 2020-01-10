package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * A Filter that only matches a single Lucene document id.
 *
 * Used for finding hits in a single document (for highlighting).
 */
public class SingleDocIdFilter extends Query {

    final int luceneDocId;

    public SingleDocIdFilter(int luceneDocId) {
        this.luceneDocId = luceneDocId;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        return new Weight((Query) null) {
            @Override
            public void extractTerms(Set<Term> terms) {
                // NOP
            }

            @Override
            public Explanation explain(LeafReaderContext context, int doc) throws IOException {
                return null;
            }

            @Override
            public float getValueForNormalization() throws IOException {
                return 1.0f;
            }

            @Override
            public void normalize(float norm, float boost) {
                // NOP
            }

            @Override
            public Scorer scorer(final LeafReaderContext ctx) throws IOException {
                return new Scorer(this) {
                    @Override
                    public int docID() {
                        return luceneDocId;
                    }

                    @Override
                    public float score() throws IOException {
                        return 1.0f;
                    }

                    @Override
                    public int freq() throws IOException {
                        return 1;
                    }

                    @Override
                    public DocIdSetIterator iterator() {
                        // Check that id could be in this segment, and bits allows this doc id
                        if (luceneDocId >= ctx.docBase) {
                            // ctx is a single segment, so use docBase to adjust the id
                            return new SingleDocIdSet(luceneDocId - ctx.docBase).iterator();
                        }
                        try {
                            return DocIdSet.EMPTY.iterator();
                        } catch (IOException e) {
                            throw BlackLabRuntimeException.wrap(e);
                        }
                    }
                };
            }
        };
    }

    @Override
    public String toString(String field) {
        return "SingleDocIdFilter(" + luceneDocId + ")";
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && luceneDocId == ((SingleDocIdFilter)obj).luceneDocId;
    }
}
