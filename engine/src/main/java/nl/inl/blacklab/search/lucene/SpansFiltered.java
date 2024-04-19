package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.ConjunctionDISI;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.search.spans.Spans;

/**
 * Apply a document Filter to a Spans.
 *
 * This allows us to only consider certain documents (say, only documents in a
 * certain domain) when executing our query.
 */
class SpansFiltered extends BLFilterSpans<BLSpans> {

    /** Keep hits from these documents. */
    private final DocIdSetIterator acceptedDocs;

    /** Two-phase approximation */
    DocIdSetIterator conjunction;

    public SpansFiltered(BLSpans spans, Scorer filterDocs) throws IOException {
        this(spans, filterDocs == null ? null : filterDocs.iterator());
    }

    public SpansFiltered(BLSpans spans, DocIdSetIterator acceptedDocs) throws IOException {
        super(spans);
        assert spans != null && acceptedDocs != null; // don't create this class if there are no hits!
        this.acceptedDocs = acceptedDocs;

        if (acceptedDocs == null || acceptedDocs.docID() == NO_MORE_DOCS)
            conjunction = DocIdSetIterator.empty();
        else {
            TwoPhaseIterator inner = in == null ? null : in.asTwoPhaseIterator();
            if (inner == null) {
                conjunction = acceptedDocs;
            } else {
                DocIdSetIterator innerApprox = inner.approximation();
                conjunction = ConjunctionDISI.intersectIterators(List.of(acceptedDocs, innerApprox));
            }
        }
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        startPos = -1;
        while (true) {
            int doc = conjunction.nextDoc();
            assert doc >= 0;
            if (doc == NO_MORE_DOCS) {
                return NO_MORE_DOCS;
            } else if (twoPhaseCurrentDocMatches()) {
                return doc;
            }
        }
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > in.docID();
        atFirstInCurrentDoc = false;
        startPos = -1;
        int doc = conjunction.advance(target);
        assert doc >= 0;
        while (doc != NO_MORE_DOCS && !twoPhaseCurrentDocMatches()) {
            doc = conjunction.nextDoc();
            assert doc >= 0;
        }
        return doc;
    }

    @Override
    public int docID() {
        return conjunction.docID();
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        assert candidate.startPosition() >= 0;

        // Accept all hits in the matching document
        // (two-phase approximation already checked our acceptedDocs)
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        return "DOCFILTER(" + in + ", " + acceptedDocs + ")";
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        if (in.docID() < conjunction.docID()) {
            // This can happen if the clause doesn't have a two-phase iterator.
            // In that case, our "conjunction" is actually just the acceptedDocs,
            // and the clause may not have been advanced yet.
            int docId = in.advance(conjunction.docID());
            if (docId == NO_MORE_DOCS) {
                return false;
            }
        }
        return super.twoPhaseCurrentDocMatches();
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        TwoPhaseIterator inner = in == null ? null : in.asTwoPhaseIterator();
        if (inner != null) {
            // wrapped instance has an approximation
            return new TwoPhaseIterator(conjunction) {
                @Override
                public boolean matches() throws IOException {
                    return inner.matches() && twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return inner.matchCost(); // underestimate
                }

                @Override
                public String toString() {
                    return "BLFilterSpans@asTwoPhaseIterator(inner=" + inner + ", in=" + in + ")";
                }
            };
        } else {
            // wrapped instance has no approximation, but
            // we can still defer matching until absolutely needed.
            return new TwoPhaseIterator(conjunction) {
                @Override
                public boolean matches() throws IOException {
                    return twoPhaseCurrentDocMatches();
                }

                @Override
                public float matchCost() {
                    return in instanceof Spans ? ((Spans) in).positionsCost() : in.cost(); // overestimate
                }

                @Override
                public String toString() {
                    return "BLFilterSpans@asTwoPhaseIterator(in=" + in + ")";
                }
            };
        }
    }
}
