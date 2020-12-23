package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.matchfilter.MatchFilter;

public class SpanQueryConstrained extends BLSpanQueryAbstract {

    MatchFilter constraint;

    ForwardIndexAccessor fiAccessor;

    public SpanQueryConstrained(BLSpanQuery clause, MatchFilter constraint, ForwardIndexAccessor fiAccessor) {
        super(BLSpanQuery.ensureSortedUnique(clause));
        this.constraint = constraint;
        this.fiAccessor = fiAccessor;
    }

    @Override
    public String toString(String field) {
        return "CONSTRAINT(" + clauses.get(0) + ", " + constraint + ")";
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> cl = rewriteClauses(reader);
        MatchFilter rewrittenConstraint = constraint.rewrite();
        if (cl == null && rewrittenConstraint == constraint)
            return this;
        if (cl == null)
            cl = clauses;
        return new SpanQueryConstrained(cl.get(0), rewrittenConstraint, fiAccessor);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        BLSpanWeight prodWeight = clauses.get(0).createWeight(searcher, needsScores);
        Map<Term, TermContext> contexts = needsScores ? getTermContexts(prodWeight) : null;
        constraint.lookupAnnotationIndices(fiAccessor);
        return new SpanWeightConstrained(prodWeight, constraint, searcher, contexts);
    }

    class SpanWeightConstrained extends BLSpanWeight {

        final BLSpanWeight prodWeight;

        private MatchFilter constraint;

        public SpanWeightConstrained(BLSpanWeight prodWeight2, MatchFilter constraint, IndexSearcher searcher,
                Map<Term, TermContext> contexts) throws IOException {
            super(SpanQueryConstrained.this, searcher, contexts);
            this.prodWeight = prodWeight2;
            this.constraint = constraint;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            prodWeight.extractTerms(terms);
        }

        @Override
        public void extractTermContexts(Map<Term, TermContext> contexts) {
            prodWeight.extractTermContexts(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spansProd = prodWeight.getSpans(context, requiredPostings);
            if (spansProd == null)
                return null;
            return new SpansConstrained(spansProd, constraint,
                    fiAccessor.getForwardIndexAccessorLeafReader(context.reader()));
        }
    }

    @Override
    public boolean hitsAllSameLength() {
        return clauses.get(0).hitsAllSameLength();
    }

    @Override
    public int hitsLengthMin() {
        return clauses.get(0).hitsLengthMin();
    }

    @Override
    public int hitsLengthMax() {
        return clauses.get(0).hitsLengthMax();
    }

    @Override
    public boolean hitsEndPointSorted() {
        return clauses.get(0).hitsEndPointSorted();
    }

    @Override
    public boolean hitsStartPointSorted() {
        return clauses.get(0).hitsStartPointSorted();
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        return clauses.get(0).hitsHaveUniqueStart();
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        return clauses.get(0).hitsHaveUniqueEnd();
    }

    @Override
    public boolean hitsAreUnique() {
        return clauses.get(0).hitsAreUnique();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost();
    }

    @Override
    public String getRealField() {
        return clauses.get(0).getRealField();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((constraint == null) ? 0 : constraint.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanQueryConstrained other = (SpanQueryConstrained) obj;
        if (constraint == null) {
            if (other.constraint != null)
                return false;
        } else if (!constraint.equals(other.constraint))
            return false;
        return true;
    }

}
