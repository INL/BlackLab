package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.spans.SpanWeight;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A SpanQuery for an AND NOT query. Produces all spans matching all the
 * "include" parts, except for those that match any span in the "exclude" part.
 */
public class SpanQueryAndNot extends BLSpanQuery {

    public static SpanGuarantees createGuarantees(List<SpanGuarantees> include, boolean hasExclude) {
        return new SpanGuaranteesAdapter() {
            @Override
            public boolean okayToInvertForOptimization() {
                // Inverting is "free" if it will still be an AND NOT query (i.e. will have a positive component).
                return producesSingleTokens() && hasExclude;
            }

            @Override
            public boolean isSingleAnyToken() {
                return include.stream().allMatch(SpanGuarantees::isSingleAnyToken) && !hasExclude;
            }

            @Override
            public boolean isSingleTokenNot() {
                return producesSingleTokens() && include.isEmpty();
            }

            @Override
            public boolean hitsAllSameLength() {
                if (include.isEmpty())
                    return true;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsAllSameLength())
                        return true;
                }
                return true;
            }

            @Override
            public int hitsLengthMin() {
                if (include.isEmpty())
                    return 1;
                int l = 0;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsLengthMin() > l)
                        l = clause.hitsLengthMin();
                }
                return l;
            }

            @Override
            public int hitsLengthMax() {
                if (include.isEmpty())
                    return 1;
                int l = Integer.MAX_VALUE;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsLengthMax() < l)
                        l = clause.hitsLengthMax();
                }
                return l;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return hitsStartPointSorted() && hitsAllSameLength();
            }

            @Override
            public boolean hitsStartPointSorted() {
                return true;
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                if (include.isEmpty())
                    return true;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsHaveUniqueStart())
                        return true;
                }
                return true;
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                if (include.isEmpty())
                    return true;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsHaveUniqueEnd())
                        return true;
                }
                return true;
            }

            @Override
            public boolean hitsAreUnique() {
                if (include.isEmpty()) {
                    // pure not query always produces unique hits (all tokens that are not part of the matches)
                    return true;
                }
                for (SpanGuarantees clause : include) {
                    if (!clause.hitsAreUnique()) {
                        // At least one clause has multiple hits with same start/end, therefore the resulting matches
                        // may not be unique (note that with how SpansAnd currently works, results will probably have
                        // unique start/end but be technically incorrect, because they didn't capture all possible
                        // match info)
                        return false;
                    }
                }
                // All clauses have unique spans, so we can guarantee the resulting matches are
                return true;
            }

            @Override
            public boolean hitsCanOverlap() {
                if (include.isEmpty()) {
                    // pure not query always produces nonoverlapping hits
                    // (all tokens that are not part of the matches)
                    return false;
                }
                if (!hitsAreUnique()) {
                    // Even if one of the clauses has non-overlapping hits,
                    // if another clause has duplicate hits, this query will
                    // still produce duplicates (it combinatorically combines
                    // any duplicates to ensure we get all combinations of match info)
                    return true;
                }
                for (SpanGuarantees clause : include) {
                    if (!clause.hitsCanOverlap()) {
                        // At least one clause has nonoverlapping hits,
                        // so the resulting hits can never be overlapping.
                        return false;
                    }
                }
                // All clauses have overlapping hits.
                return true;
            }
        };
    }

    private final List<BLSpanQuery> include;

    private final List<BLSpanQuery> exclude;

    public SpanQueryAndNot(List<BLSpanQuery> include, List<BLSpanQuery> exclude) {
        super(include != null && !include.isEmpty() ? include.get(0).queryInfo : exclude != null && !exclude.isEmpty() ? exclude.get(0).queryInfo : null);
        this.include = include == null ? new ArrayList<>() : include;
        this.exclude = exclude == null ? new ArrayList<>() : exclude;
        if (this.include.size() == 0 && this.exclude.size() == 0)
            throw new IllegalArgumentException("ANDNOT query without clauses");
        checkBaseFieldName();

        List<SpanGuarantees> clauseGuarantees = this.include.stream()
                .map(BLSpanQuery::guarantees)
                .collect(Collectors.toList());
        this.guarantees = createGuarantees(clauseGuarantees, !this.exclude.isEmpty());
    }

    private void checkBaseFieldName() {
        if (!include.isEmpty()) {
            String baseFieldName = AnnotatedFieldNameUtil.getBaseName(include.get(0).getField());
            for (BLSpanQuery clause : include) {
                String f = AnnotatedFieldNameUtil.getBaseName(clause.getField());
                if (!baseFieldName.equals(f))
                    throw new BlackLabRuntimeException("Mix of incompatible fields in query ("
                            + baseFieldName + " and " + f + ")");
            }
        }
    }

    @Override
    public BLSpanQuery inverted() {
        if (exclude.isEmpty()) {
            // In this case, it's better to just wrap this in TextPatternNot,
            // so it will be recognized by other rewrite()s.
            return super.inverted();
        }

        // ! ( (a & b) & !(c & d) ) --> !a | !b | (c & d)
        List<BLSpanQuery> inclNeg = new ArrayList<>();
        for (BLSpanQuery tp : include) {
            inclNeg.add(tp.inverted());
        }
        if (exclude.size() == 1)
            inclNeg.add(exclude.get(0));
        else
            inclNeg.add(new SpanQueryAndNot(exclude, null));
        return new BLSpanOrQuery(inclNeg.toArray(new BLSpanQuery[0]));
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {

        // Flatten nested AND queries, and invert negative-only clauses.
        // This doesn't change the query because the AND operator is associative.
        boolean anyRewritten = false;
        List<BLSpanQuery> flatCl = new ArrayList<>();
        List<BLSpanQuery> flatNotCl = new ArrayList<>();
        boolean isNot = false;
        for (List<BLSpanQuery> cl : Arrays.asList(include, exclude)) {
            for (BLSpanQuery child : cl) {
                List<BLSpanQuery> clPos = isNot ? flatNotCl : flatCl;
                List<BLSpanQuery> clNeg = isNot ? flatCl : flatNotCl;
                boolean isTPAndNot = child instanceof SpanQueryAndNot;
                if (!isTPAndNot && child.guarantees().isSingleTokenNot()) {
                    // "Switch sides": invert the clause, and
                    // swap the lists we add clauses to.
                    child = child.inverted();
                    List<BLSpanQuery> temp = clPos;
                    clPos = clNeg;
                    clNeg = temp;
                    anyRewritten = true;
                    isTPAndNot = child instanceof SpanQueryAndNot;
                }
                if (isTPAndNot) {
                    // Flatten.
                    // Child AND operation we want to flatten into this AND operation.
                    // Replace the child, incorporating its children into this AND operation.
                    clPos.addAll(((SpanQueryAndNot) child).include);
                    clNeg.addAll(((SpanQueryAndNot) child).exclude);
                    anyRewritten = true;
                } else {
                    // Just add it.
                    clPos.add(child);
                }
            }
            isNot = true;
        }

        // Rewrite clauses, and again flatten/invert if necessary.
        List<BLSpanQuery> rewrCl = new ArrayList<>();
        List<BLSpanQuery> rewrNotCl = new ArrayList<>();
        isNot = false;
        for (List<BLSpanQuery> cl : Arrays.asList(flatCl, flatNotCl)) {
            for (BLSpanQuery child : cl) {
                List<BLSpanQuery> clPos = isNot ? rewrNotCl : rewrCl;
                List<BLSpanQuery> clNeg = isNot ? rewrCl : rewrNotCl;
                BLSpanQuery rewritten = child.rewrite(reader);
                boolean isTPAndNot = rewritten instanceof SpanQueryAndNot;
                if (!isTPAndNot && rewritten.guarantees().isSingleTokenNot()) {
                    // "Switch sides": invert the clause, and
                    // swap the lists we add clauses to.
                    rewritten = rewritten.inverted();
                    List<BLSpanQuery> temp = clPos;
                    clPos = clNeg;
                    clNeg = temp;
                    anyRewritten = true;
                    isTPAndNot = rewritten instanceof SpanQueryAndNot;
                }
                if (isTPAndNot) {
                    // Flatten.
                    // Child AND operation we want to flatten into this AND operation.
                    // Replace the child, incorporating its children into this AND operation.
                    clPos.addAll(((SpanQueryAndNot) rewritten).include);
                    clNeg.addAll(((SpanQueryAndNot) rewritten).exclude);
                    anyRewritten = true;
                } else {
                    // Just add it.
                    clPos.add(rewritten);
                    if (rewritten != child)
                        anyRewritten = true;
                }
            }
            isNot = true;
        }

        if (rewrCl.isEmpty()) {
            // All-negative; node should be rewritten to OR.
            if (rewrNotCl.size() == 1)
                return rewrNotCl.get(0).inverted().rewrite(reader);
            return (new BLSpanOrQuery(rewrNotCl.toArray(new BLSpanQuery[0]))).inverted().rewrite(reader);
        }

        if (rewrCl.size() > 1) {
            // If there's more than one positive clause, remove the super general "match all" clause.
            rewrCl = rewrCl.stream().filter(cl -> !(cl instanceof SpanQueryAnyToken)).collect(Collectors.toList());
        }

        if (rewrCl.size() == 1 && rewrNotCl.isEmpty()) {
            // Single positive clause
            return rewrCl.get(0);
        }

        if (!anyRewritten && exclude.isEmpty()) {
            // Nothing needs to be rewritten.
            return this;
        }

        // Combination of positive and possibly negative clauses
        BLSpanQuery includeResult = rewrCl.size() == 1 ? rewrCl.get(0) : new SpanQueryAndNot(rewrCl, null);
        if (rewrNotCl.isEmpty())
            return includeResult.rewrite(reader);
        BLSpanQuery excludeResult = rewrNotCl.size() == 1 ? rewrNotCl.get(0)
                : new BLSpanOrQuery(rewrNotCl.toArray(new BLSpanQuery[0]));
        return new SpanQueryPositionFilter(includeResult, excludeResult, SpanQueryPositionFilter.Operation.MATCHES,
                true).rewrite(reader);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            include.forEach(cl -> cl.visit(visitor.getSubVisitor(Occur.MUST, this)));
            exclude.forEach(cl -> cl.visit(visitor.getSubVisitor(Occur.MUST_NOT, this)));
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((exclude == null) ? 0 : exclude.hashCode());
        result = prime * result + ((include == null) ? 0 : include.hashCode());
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
        SpanQueryAndNot other = (SpanQueryAndNot) obj;
        if (exclude == null) {
            if (other.exclude != null)
                return false;
        } else if (!exclude.equals(other.exclude))
            return false;
        if (include == null) {
            if (other.include != null)
                return false;
        } else if (!include.equals(other.include))
            return false;
        return true;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (!exclude.isEmpty())
            throw new BlackLabRuntimeException("Query should've been rewritten! (exclude clauses left)");

        List<BLSpanWeight> weights = new ArrayList<>();
        for (BLSpanQuery clause : include) {
            weights.add(clause.createWeight(searcher, scoreMode, boost));
        }
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(weights.toArray(new SpanWeight[0])) : null;
        return new SpanWeightAnd(weights, searcher, contexts, boost);
    }

    class SpanWeightAnd extends BLSpanWeight {

        final List<BLSpanWeight> weights;

        public SpanWeightAnd(List<BLSpanWeight> weights, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryAndNot.this, searcher, terms, boost);
            this.weights = weights;
        }

        @Override
        @Deprecated
        public void extractTerms(Set<Term> terms) {
            for (BLSpanWeight weight : weights) {
                weight.extractTerms(terms);
            }
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            for (BLSpanWeight weight : weights) {
                weight.extractTermStates(contexts);
            }
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans combi = weights.get(0).getSpans(context, requiredPostings);
            if (combi == null)
                return null; // if no hits in one of the clauses, no hits in AND query
            combi = BLSpans.ensureStartPointSorted(combi);
            for (int i = 1; i < weights.size(); i++) {
                BLSpans si = weights.get(i).getSpans(context, requiredPostings);
                if (si == null)
                    return null; // if no hits in one of the clauses, no hits in AND query
                if (!si.guarantees().hitsStartPointSorted())
                    si = BLSpans.ensureStartPointSorted(si);
                if (combi.guarantees().hitsAreUnique() && si.guarantees().hitsAreUnique()) {
                    // No duplicate spans with different match info; use the faster version.
                    combi = new SpansAndSimple(combi, si);
                } else {
                    // We need to use the slower version that takes duplicate spans into account and produces all
                    // combinations.
                    combi = new SpansAnd(combi, si);
                }
            }
            return combi;
        }
    }

    @Override
    public String toString(String field) {
        if (exclude.isEmpty())
            return "AND(" + clausesToString(field, include) + ")";
        return "ANDNOT([" + clausesToString(field, include) + "], [" + clausesToString(field, exclude) + "])";
    }

    @Override
    public String getField() {
        if (!include.isEmpty())
            return include.get(0).getField();
        if (!exclude.isEmpty())
            return exclude.get(0).getField();
        throw new BlackLabRuntimeException("Query has no clauses");
    }

    @Override
    public String getRealField() {
        if (!include.isEmpty())
            return include.get(0).getRealField();
        if (!exclude.isEmpty())
            return exclude.get(0).getRealField();
        throw new BlackLabRuntimeException("Query has no clauses");
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        if (!exclude.isEmpty())
            throw new BlackLabRuntimeException("Query should've been rewritten! (exclude clauses left)");
        List<NfaState> nfaClauses = new ArrayList<>();
//		List<NfaState> dangling = new ArrayList<>();
        for (BLSpanQuery clause : include) {
            Nfa nfa = clause.getNfa(fiAccessor, direction);
            nfaClauses.add(nfa.getStartingState());
//			dangling.addAll(nfa.getDanglingArrows());
        }
        NfaState andAcyclic = NfaState.and(false, nfaClauses);
        return new Nfa(andAcyclic, List.of(andAcyclic));
    }

    @Override
    public boolean canMakeNfa() {
        if (!exclude.isEmpty())
            return false;
        for (BLSpanQuery clause : include) {
            if (!clause.canMakeNfa())
                return false;
        }
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        // Excludes should have been rewritten, so we only look at includes.
        // We return the least frequent clause since we can skip over the more
        // frequent ones, or match them using the forward index.
        long cost = Integer.MAX_VALUE;
        for (BLSpanQuery clause : include) {
            cost = Math.min(cost, clause.reverseMatchingCost(reader));
        }
        return cost;
    }

    @Override
    public int forwardMatchingCost() {
        // Add the costs of our clauses.
        int cost = 1;
        for (BLSpanQuery cl : include) {
            cost += cl.forwardMatchingCost();
        }
        return cost * 2 / 3; // we expect to be able to short-circuit AND in a significant number of cases
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        for (BLSpanQuery cl: include) {
            cl.setQueryInfo(queryInfo);
        }
        for (BLSpanQuery cl: exclude) {
            cl.setQueryInfo(queryInfo);
        }
    }
}
