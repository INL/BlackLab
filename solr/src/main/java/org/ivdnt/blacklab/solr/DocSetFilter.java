package org.ivdnt.blacklab.solr;

import java.io.IOException;
import java.util.Objects;
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
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocSet;

import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.ints.IntBigListIterator;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * A Filter that matches Lucene documents from a set of global doc ids.
 * <p>
 * Used to filter BlackLab's span results using Solr's doc result set.
 */
public class DocSetFilter extends Query {

    private final IntBigArrayBigList acceptedDocs;

    /**
     * Construct a DocSetFilter from an sorted list of global doc ids.
     *
     * @param acceptedDocs sorted list of global doc ids
     */
    public DocSetFilter(DocSet acceptedDocs) {
        // Make a copy of docs so we can find the starting point for each per segment
        this.acceptedDocs = new IntBigArrayBigList();
        if (acceptedDocs != null) {
            DocIterator it = acceptedDocs.iterator();
            while (it.hasNext()) {
                this.acceptedDocs.add(it.nextDoc());
            }
        }
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

            @Override
            public Scorer scorer(final LeafReaderContext ctx) {
                return new Scorer(this) {
                    int current = -1;

                    @Override
                    public int docID() {
                        return current;
                    }

                    @Override
                    public float score() {
                        return 1.0f;
                    }

                    @Override
                    public DocIdSetIterator iterator() {
                        // Find docBase (or the first higher id) in accepted docs list
                        long start = IntBigArrays.binarySearch(acceptedDocs.elements(), 0, acceptedDocs.size64(), ctx.docBase);
                        // Create iterator from that point (first accepted doc in this segment)
                        IntBigListIterator acceptedDocsInLeaf = acceptedDocs.listIterator(start);
                        if (acceptedDocsInLeaf.hasNext()) {
                            return new DocIdSetIterator() {
                                @Override
                                public int docID() {
                                    return current;
                                }

                                @Override
                                public int nextDoc() {
                                    current = acceptedDocsInLeaf.nextInt() - ctx.docBase;
                                    return current;
                                }

                                @Override
                                public int advance(int i) {
                                    current = acceptedDocsInLeaf.skip(i) - ctx.docBase;
                                    return current;
                                }

                                @Override
                                public long cost() {
                                    return 0;
                                }
                            };
                        }
                        try {
                            return DocIdSet.EMPTY.iterator();
                        } catch (IOException e) {
                            throw BlackLabRuntimeException.wrap(e);
                        }
                    }

                    @Override
					public float getMaxScore(int upTo) {
						return 0;
					}
                };
            }

			@Override
			public boolean isCacheable(LeafReaderContext ctx) {
				return false;
			}

        };
    }

    @Override
    public String toString(String field) {
        return "DocSetFilter(" + acceptedDocs.size64() + " docs)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DocSetFilter that = (DocSetFilter) o;
        return acceptedDocs.equals(that.acceptedDocs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acceptedDocs);
    }
}
