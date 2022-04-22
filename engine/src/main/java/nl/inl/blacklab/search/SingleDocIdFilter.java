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
import org.apache.lucene.search.ScoreMode;
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
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) {
        return new Weight(null) {
            @Override
            public void extractTerms(Set<Term> terms) {
                // NOP
            }

            @Override
            public Explanation explain(LeafReaderContext context, int doc) {
                return null;
            }

            /*zyw @Override
            public float getValueForNormalization() throws IOException {
                return 1.0f;
            }

            @Override
            public void normalize(float norm, float boost) {
                // NOP
            }*/

            @Override
            public Scorer scorer(final LeafReaderContext ctx) {
                return new Scorer(this) {
                    @Override
                    public int docID() {
                        return luceneDocId;
                    }

                    @Override
                    public float score() {
                        return 1.0f;
                    }

                    /*zyw @Override
                    public int freq() throws IOException {
                        return 1;
                    }*/

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

					@Override
					public float getMaxScore(int upTo) {
						// TODO Auto-generated method stub
						return 0;
					}
                };
            }

			@Override
			public boolean isCacheable(LeafReaderContext ctx) {
				// TODO Auto-generated method stub
				return false;
			}

        };
    }

    @Override
    public String toString(String field) {
        return "SingleDocIdFilter(" + luceneDocId + ")";
    }

    @Override
  	public int hashCode() {
  		final int prime = 31;
  		int result = 1;
  		result = prime * result + luceneDocId;
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
  		SingleDocIdFilter other = (SingleDocIdFilter) obj;
  		if (luceneDocId != other.luceneDocId)
  			return false;
  		return true;
  	}
}
