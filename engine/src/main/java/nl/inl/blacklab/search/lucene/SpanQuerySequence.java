package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.spans.SpanWeight;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombiner;
import nl.inl.util.StringUtil;

/**
 * Combines spans, keeping only combinations of hits that occur one after the
 * other. The order is significant: a hit from the first span must be followed
 * by a hit from the second.
 * <p>
 * Note that this class is different from
 * org.apache.lucene.search.spans.SpanNearQuery: it tries to make sure it
 * generates *all* possible sequence matches. SpanNearQuery doesn't do this;
 * once a hit is used in a SpanNearQuery match, it advances to the next hit.
 * <p>
 * In the future, this class could be expanded to make the exact behaviour
 * configurable: find all matches / find longest matches / find shortest matches
 * / ...
 * <p>
 * See SpanSequenceRaw for details on the matching process.
 */
public class SpanQuerySequence extends BLSpanQueryAbstract {
    protected static final Logger logger = LogManager.getLogger(SpanQuerySequence.class);

    public SpanQuerySequence(BLSpanQuery first, BLSpanQuery second) {
        super(first, second);
    }

    public SpanQuerySequence(List<BLSpanQuery> clauscol) {
        super(clauscol);
    }

    public SpanQuerySequence(BLSpanQuery[] clauses) {
        super(clauses);
    }

    /**
     * Flatten nested sequences in clauses array.
     * <p>
     * Flattens in-place.
     *
     * @param clauses clauses which may need flattening
     * @return true if any rewriting was done, false if not
     */
    private static boolean flattenSequence(List<BLSpanQuery> clauses) {
        boolean anyRewritten = false;
        for (int i = 0; i < clauses.size(); i++) {
            BLSpanQuery child = clauses.get(i);
            if (child instanceof SpanQuerySequence) {
                clauses.remove(i);
                clauses.addAll(i, ((SpanQuerySequence) child).getClauses());
                --i;
                anyRewritten = true;
            }
        }
        return anyRewritten;
    }

    /**
     * Try to match separate start and end tags in this sequence, and convert into a
     * position filter (e.g. containing) query.
     * <p>
     * For example: <s> []* 'bla' []* </s> ==> <s/> containing 'bla'
     *
     * @param clauses clauses in which to find matching tags
     * @return true if any rewriting was done, false if not
     */
    protected boolean matchingTagsToPosFilter(List<BLSpanQuery> clauses) {
        boolean anyRewritten = false;

        // Try to match separate start and end tags in this sequence, and convert into a
        // containing query. (<s> []* 'bla' []* </s> ==> <s/> containing 'bla')
        for (int i = 0; i < clauses.size(); i++) {
            BLSpanQuery clause = clauses.get(i);
            if (clause instanceof SpanQueryEdge) {
                SpanQueryEdge start = (SpanQueryEdge) clause;
                if (!start.isRightEdge()) {
                    String tagName = start.getElementName();
                    if (tagName != null) {
                        // Start tag found. Is there a matching end tag?
                        for (int j = i + 1; j < clauses.size(); j++) {
                            BLSpanQuery clause2 = clauses.get(j);
                            if (clause2 instanceof SpanQueryEdge) {
                                SpanQueryEdge end = (SpanQueryEdge) clause2;
                                if (end.isRightEdge() && end.getElementName().equals(tagName)) {
                                    // Found start and end tags in sequence. Convert to containing
                                    // query.
                                    List<BLSpanQuery> search = new ArrayList<>();
                                    clauses.remove(i); // start tag
                                    for (int k = 0; k < j - i - 1; k++) {
                                        search.add(clauses.remove(i));
                                    }
                                    clauses.remove(i); // end tag
                                    boolean startAny = false;
                                    if (search.get(0) instanceof SpanQueryAnyToken) {
                                        SpanQueryAnyToken any1 = (SpanQueryAnyToken) search.get(0);
                                        if (any1.hitsLengthMin() == 0 && any1.hitsLengthMax() == MAX_UNLIMITED) {
                                            startAny = true;
                                            search.remove(0);
                                        }
                                    }
                                    boolean endAny = false;
                                    int last = search.size() - 1;
                                    if (search.get(last) instanceof SpanQueryAnyToken) {
                                        SpanQueryAnyToken any2 = (SpanQueryAnyToken) search.get(last);
                                        if (any2.hitsLengthMin() == 0 && any2.hitsLengthMax() == MAX_UNLIMITED) {
                                            endAny = true;
                                            search.remove(last);
                                        }
                                    }
                                    BLSpanQuery producer = start.getClause();
                                    BLSpanQuery filter = new SpanQuerySequence(search.toArray(new BLSpanQuery[0]));
                                    SpanQueryPositionFilter.Operation op;
                                    if (startAny) {
                                        if (endAny) {
                                            op = SpanQueryPositionFilter.Operation.CONTAINING;
                                        } else {
                                            op = SpanQueryPositionFilter.Operation.CONTAINING_AT_END;
                                        }
                                    } else {
                                        if (endAny) {
                                            op = SpanQueryPositionFilter.Operation.CONTAINING_AT_START;
                                        } else {
                                            op = SpanQueryPositionFilter.Operation.MATCHES;
                                        }
                                    }
                                    clauses.add(i, new SpanQueryPositionFilter(producer, filter, op, false));
                                    anyRewritten = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return anyRewritten;
    }

    static boolean rewriteClauses(List<BLSpanQuery> clauses, IndexReader reader) throws IOException {
        boolean anyRewritten = false;
        // Rewrite all clauses.
        for (int i = 0; i < clauses.size(); i++) {
            BLSpanQuery child = clauses.get(i);
            BLSpanQuery rewritten = child.rewrite(reader);
            if (child != rewritten) {
                // Replace the child with the rewritten version
                anyRewritten = true;
                clauses.set(i, rewritten);
            }
        }
        return anyRewritten;
    }

    static boolean optimizeClauses(List<BLSpanQuery> clauses, IndexReader reader) throws IOException {
        boolean anyRewritten = false;
        // Rewrite all clauses.
        for (int i = 0; i < clauses.size(); i++) {
            BLSpanQuery child = clauses.get(i);
            BLSpanQuery rewritten = child.optimize(reader);
            if (child != rewritten) {
                // Replace the child with the rewritten version
                anyRewritten = true;
                clauses.set(i, rewritten);
            }
        }
        return anyRewritten;
    }

    private static String prioName(int prio) {
        return prio == ClauseCombiner.CANNOT_COMBINE ? "CANNOT_COMBINE" : Integer.toString(prio);
    }

    static boolean combineAdjacentClauses(List<BLSpanQuery> cl, IndexReader reader,
            Set<ClauseCombiner> combiners) {

        boolean anyRewritten = false;

        // Rewrite adjacent clauses according to rewriting precedence rules
        boolean anyRewrittenThisCycle = true;
        int pass = 0;
        boolean traceOptimization = BlackLab.config().getLog().getTrace().isOptimization();
        BLSpanQuery searchLogger = !cl.isEmpty() && traceOptimization ? cl.get(0) : null;
        if (traceOptimization)
            logger.debug("SpanQuerySequence.combineAdjacentClauses() start");
        while (anyRewrittenThisCycle) {
            if (traceOptimization) {
                logger.debug("Clauses before " + StringUtil.ord(pass) + " pass: " + StringUtils.join(cl, ", "));
                pass++;
            }

            anyRewrittenThisCycle = false;

            // Find the highest-priority rewrite possible
            int highestPrio = ClauseCombiner.CANNOT_COMBINE, highestPrioIndex = -1;
            ClauseCombiner highestPrioCombiner = null;
            BLSpanQuery first, second;
            for (int i = 1; i < cl.size(); i++) {
                // See if any combiners apply, and if the priority is higher than found so far.
                first = cl.get(i - 1);
                second = cl.get(i);
                for (ClauseCombiner combiner : combiners) {
                    int prio = combiner.priority(first, second, reader);
                    if (searchLogger != null) {
                        if (prio == ClauseCombiner.CANNOT_COMBINE)
                            logger.debug("(Cannot apply " + combiner + "(" + first + ", " + second + "))");
                        else
                            logger.debug("Can apply " + combiner + "(" + first + ", " + second + "), priority: " + prioName(prio));
                    }
                    if (prio < highestPrio) {
                        highestPrio = prio;
                        highestPrioIndex = i;
                        highestPrioCombiner = combiner;
                    }
                }
            }
            // Any combiners found?
            if (highestPrio < ClauseCombiner.CANNOT_COMBINE) {
                // Yes, execute the highest-prio combiner
                first = cl.get(highestPrioIndex - 1);
                second = cl.get(highestPrioIndex);
                if (traceOptimization)
                    logger.info("Execute lowest prio number combiner: " + highestPrioCombiner + "(" + first + ", " + second + ")");
                first = cl.get(highestPrioIndex - 1);
                second = cl.get(highestPrioIndex);
                BLSpanQuery combined = highestPrioCombiner.combine(first, second, reader);
                // (we used to rewrite() combined here just to be safe, but that could break optimizations later)
                cl.remove(highestPrioIndex);
                cl.set(highestPrioIndex - 1, combined);
                anyRewrittenThisCycle = true;
            }
            if (anyRewrittenThisCycle)
                anyRewritten = true;
        }
        if (traceOptimization)
            logger.info("Cannot combine any other clauses. Result: " + StringUtils.join(cl, ", "));

        return anyRewritten;
    }

    @Override
    public BLSpanQuery optimize(IndexReader reader) throws IOException {
        super.optimize(reader);
        // Make a copy, because our methods rewrite things in-place.
        List<BLSpanQuery> cl = new ArrayList<>(clauses);

        BlackLabIndex index = BlackLab.indexFromReader(null, reader, true);
        boolean canDoNfaMatching = isCanDoNfaMatching(index);
        boolean anyRewritten = performQueryOptimizations(index, cl, canDoNfaMatching);

        // Optimize each clause, and flatten again if necessary
        anyRewritten |= optimizeClauses(cl, reader);

        if (!anyRewritten) {
            // Nothing rewritten. If this is a sequence of length one, just return the clause;
            // otherwise return this object unchanged.
            if (cl.size() == 1)
                return cl.get(0);
            return this;
        }
        return new SpanQuerySequence(cl.toArray(new BLSpanQuery[0]));
    }

    private boolean performQueryOptimizations(BlackLabIndex index, List<BLSpanQuery> cl, boolean canDoNfaMatching) {
        // Flatten nested sequences.
        // This doesn't change the query because the sequence operator is associative.
        boolean anyRewritten = flattenSequence(cl);

        // Find matching tags and rewrite them to position filter (e.g. containing) to execute more
        // efficiently
        anyRewritten |= matchingTagsToPosFilter(cl);

        // Try to combine adjacent clauses into more efficient ones.
        // We do this before rewrite (as well as after) specifically to find clauses that are slow
        // because
        // of regular expressions matching many terms (e.g. "s.*" or ".*s") and match these using an
        // NFA instead.
        // By doing it before rewriting, we save the time to expand the regex to all its matching
        // terms, as well
        // as dealing with each of these (sometimes frequent) terms, which can be significant.
        anyRewritten |= combineAdjacentClauses(cl, index.reader(), ClauseCombiner.all(canDoNfaMatching));
        return anyRewritten;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        // Make a copy, because our methods rewrite things in-place.
        List<BLSpanQuery> cl = new ArrayList<>(clauses);

        BlackLabIndex index = BlackLab.indexFromReader(null, reader, true);
        boolean canDoNfaMatching = isCanDoNfaMatching(index);
        boolean anyRewritten = performQueryOptimizations(index, cl, canDoNfaMatching);

        // Rewrite each clause, and flatten again if necessary
        anyRewritten |= rewriteClauses(cl, reader);
        if (anyRewritten)
            flattenSequence(cl);

        // Again, try to combine adjacent clauses into more efficient ones. Rewriting clauses may have
        // generated new opportunities for combining clauses.
        anyRewritten |= combineAdjacentClauses(cl, reader,  ClauseCombiner.all(canDoNfaMatching));

        // If any part of the sequence matches the empty sequence, we must
        // rewrite it to several alternatives combined with OR. Do so now.
        List<List<BLSpanQuery>> results = makeAlternatives(cl, reader);
        if (results.size() == 1 && !anyRewritten) {
            // Nothing rewritten. If this is a sequence of length one, just return the clause;
            // otherwise return this object unchanged.
            List<BLSpanQuery> seq = results.get(0);
            if (seq.size() == 1)
                return seq.get(0);
            return this;
        }
        List<BLSpanQuery> orCl = new ArrayList<>();
        for (List<BLSpanQuery> seq : results) {
            if (seq.size() == 1)
                orCl.add(seq.get(0));
            else
                orCl.add(new SpanQuerySequence(seq.toArray(new BLSpanQuery[0])));
        }
        if (orCl.size() == 1)
            return orCl.get(0);
        return new BLSpanOrQuery(orCl.toArray(new BLSpanQuery[0])).rewrite(reader);
    }

    private boolean isCanDoNfaMatching(BlackLabIndex index) {
        boolean canDoNfaMatching = false;
        if (index instanceof BlackLabIndexAbstract) {
            canDoNfaMatching = ((BlackLabIndexAbstract) index).canDoNfaMatching();
        }
        return canDoNfaMatching;
    }

    /**
     * For possibly empty clauses, combine them with a neighbour into a binary-tree
     * structure. This differs from the approach of makeAlternatives() which
     * produces an OR of several longer sequences. That approach is probably more
     * efficient with Lucene (because it allows more optimizations on the longer
     * sequences produced), while this approach is probably more efficient for NFAs
     * (because we don't have to follow many long paths in the NFA).
     *
     * @param cl clauses
     * @param reader index reader
     * @return alternatives tree
     */
    @SuppressWarnings("unused")
    private static boolean makeAlternativesLocally(List<BLSpanQuery> cl, IndexReader reader) {
        boolean anyRewritten = false;
        while (true) {
            // Find two clauses to combine to OR, preferring to combine one that matches
            // the empty sequence with one that does not.
            int bestIndex = -1;
            boolean bestBothEmpty = true;
            for (int i = 1; i < cl.size(); i++) {
                BLSpanQuery first = cl.get(i - 1);
                BLSpanQuery second = cl.get(i);
                boolean firstEmpty = first.matchesEmptySequence();
                boolean secondEmpty = second.matchesEmptySequence();
                // Does either clause matcht the empty sequence, and are these two
                // the best candidates to combine right now?
                if ((firstEmpty || secondEmpty) && bestBothEmpty || (!bestBothEmpty && (!firstEmpty || !secondEmpty))) {
                    bestBothEmpty = firstEmpty && secondEmpty;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0)
                return anyRewritten; // we're done
            // Combine the clauses we found
            BLSpanQuery first = cl.get(bestIndex - 1);
            BLSpanQuery second = cl.get(bestIndex);
            boolean firstEmpty = first.matchesEmptySequence();
            boolean secondEmpty = second.matchesEmptySequence();
            BLSpanQuery combi;
            BLSpanQuery both = new SpanQuerySequence(first, second);
            if (firstEmpty && secondEmpty) {
                // 4 alternatives: neither (hence the {0,1} repetition), first only, second only, or both
                combi = new SpanQueryRepetition(new BLSpanOrQuery(first, second, both), 0, 1);
            } else if (firstEmpty) {
                // 2 alternatives: second only, or both
                combi = new BLSpanOrQuery(second, both);
            } else {
                // 2 alternatives: first only, or both
                combi = new BLSpanOrQuery(first, both);
            }
            cl.remove(bestIndex - 1);
            cl.set(bestIndex - 1, combi);
            anyRewritten = true;
        }
    }

    /**
     * Given translated clauses, builds several alternatives and combines them with
     * OR.
     * <p>
     * This is necessary because of how sequence matching works: first the hits in
     * each of the clauses are located, then we try to detect valid sequences by
     * looking at these hits. But when a clause also matches the empty sequence, you
     * may miss valid sequence matches because there's no hit in the clause to
     * combine with the hits from other clauses.
     *
     * @param parts translation results for each of the clauses so far
     * @param reader the index reader
     * @return several alternatives combined with or
     */
    List<List<BLSpanQuery>> makeAlternatives(List<BLSpanQuery> parts, IndexReader reader) throws IOException {
        if (parts.size() == 1) {
            // Last clause in the sequence; just return it
            // (noEmpty() version because we will build alternatives
            // in the caller if the input matched the empty sequence)
            return List.of(List.of(parts.get(0).noEmpty().rewrite(reader)));
        }

        // Recursively determine the query for the "tail" of the list,
        // and whether it matches the empty sequence or not.
        List<BLSpanQuery> partsTail = parts.subList(1, parts.size());
        boolean restMatchesEmpty = true;
        for (BLSpanQuery part : partsTail) {
            if (!part.matchesEmptySequence()) {
                restMatchesEmpty = false;
                break;
            }
        }
        List<List<BLSpanQuery>> altTail = makeAlternatives(partsTail, reader);

        // Now, add the head part and check if that matches the empty sequence.
        return combine(parts.get(0), altTail, restMatchesEmpty, reader);
    }

    private static List<List<BLSpanQuery>> combine(BLSpanQuery head, List<List<BLSpanQuery>> tailAlts,
            boolean tailMatchesEmpty,
            IndexReader reader) throws IOException {
        List<List<BLSpanQuery>> results = new ArrayList<>();
        BLSpanQuery headNoEmpty = head.noEmpty().rewrite(reader);
        boolean headMatchesEmpty = head.matchesEmptySequence();
        for (List<BLSpanQuery> tailAlt : tailAlts) {
            // Add head in front of each tail alternative
            List<BLSpanQuery> n = new ArrayList<>(tailAlt);
            n.add(0, headNoEmpty);
            results.add(n);

            // If head can be empty, also add original tail alternative
            if (headMatchesEmpty)
                results.add(tailAlt);
        }
        // If tail can be empty, also add the head separately
        if (tailMatchesEmpty)
            results.add(List.of(headNoEmpty));
        return results;
    }

    @Override
    public boolean matchesEmptySequence() {
        for (BLSpanQuery cl : clauses) {
            if (!cl.matchesEmptySequence())
                return false;
        }
        return true;
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        throw new BlackLabRuntimeException("Sequence should have been rewritten!");
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        List<BLSpanWeight> weights = new ArrayList<>();
        for (BLSpanQuery clause : clauses) {
            weights.add(clause.createWeight(searcher, scoreMode, boost));
        }
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(weights.toArray(new SpanWeight[0])) : null;
        return new SpanWeightSequence(weights, searcher, contexts, boost);
    }

    class SpanWeightSequence extends BLSpanWeight {

        final List<BLSpanWeight> weights;

        public SpanWeightSequence(List<BLSpanWeight> weights, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQuerySequence.this, searcher, terms, boost);
            this.weights = weights;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            for (SpanWeight weight : weights) {
                weight.extractTerms(terms);
            }
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            for (SpanWeight weight : weights) {
                weight.extractTermStates(contexts);
            }
        }

        private class CombiPart {
            BLSpans spans;

            boolean uniqueStart;

            boolean uniqueEnd;

            boolean startSorted;

            boolean endSorted;

            boolean sameLength;

            public CombiPart(BLSpanWeight weight, final LeafReaderContext context, Postings requiredPostings)
                    throws IOException {
                this.spans = weight.getSpans(context, requiredPostings);
                BLSpanQuery q = (BLSpanQuery) weight.getQuery();
                if (q != null) {
                    this.uniqueStart = q.hitsHaveUniqueStart();
                    this.uniqueEnd = q.hitsHaveUniqueEnd();
                    this.startSorted = q.hitsStartPointSorted();
                    this.endSorted = q.hitsEndPointSorted();
                    this.sameLength = q.hitsAllSameLength();
                }
            }

            public CombiPart(BLSpans spans, boolean hitsHaveUniqueStart, boolean hitsHaveUniqueEnd,
                    boolean hitsStartPointSorted,
                    boolean hitsEndPointSorted, boolean hitsAllSameLength) {
                super();
                this.spans = spans;
                this.uniqueStart = hitsHaveUniqueStart;
                this.uniqueEnd = hitsHaveUniqueEnd;
                this.startSorted = hitsStartPointSorted;
                this.endSorted = hitsEndPointSorted;
                this.sameLength = hitsAllSameLength;
            }

            @Override
            public String toString() {
                return spans.toString();
            }

        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            List<CombiPart> parts = new ArrayList<>();
            for (BLSpanWeight weight : weights) {
                CombiPart part = new CombiPart(weight, context, requiredPostings);
                if (part.spans == null)
                    return null;
                parts.add(part);
            }

            // First, combine as many clauses as possible into SpansSequenceSimple,
            // which works for simple clauses and is the most efficient to execute.
            //
            // NOTE: it might be even better to favour combining low-frequency terms first,
            // as that minimizes useless skipping through non-matching docs (but this should
            // be solved by two-phase iterators now).
            for (int i = 1; i < parts.size(); i++) {
                CombiPart first = parts.get(i - 1);
                CombiPart second = parts.get(i);
                CombiPart newPart;
                if (first.uniqueEnd && first.endSorted && second.startSorted && second.uniqueStart) {
                    // We can take a shortcut because of what we know about the Spans we're
                    // combining.
                    BLSpans newSpans = new SpansSequenceSimple(first.spans, second.spans);
                    newPart = new CombiPart(newSpans, first.uniqueStart, second.uniqueEnd,
                            first.startSorted, second.sameLength,
                            first.sameLength && second.sameLength);
                    parts.remove(i - 1);
                    parts.set(i - 1, newPart);
                    i--;
                }
            }

            // Next, see if we have SpansExpansion that we can resolve using SpansSequenceWithGap.
            for (int i = 1; i < parts.size(); i++) {
                CombiPart first = parts.get(i - 1);
                CombiPart second = parts.get(i);
                BLSpans sp1 = first.spans;
                BLSpans sp2 = second.spans;
                if (sp1 instanceof SpansExpansionRaw && ((SpansExpansionRaw)sp1).direction() == Direction.RIGHT) {
                    // First is a forward expansion. Make a SpansSequenceWithGap.
                    BLSpans newSpans;
                    if (sp2 instanceof SpansExpansionRaw && ((SpansExpansionRaw)sp2).direction() == Direction.RIGHT) {
                        // Second is a forward expansion too. Make the whole resulting clause a forward expansion
                        //   instead, so we can repeat the sequence-with-gaps trick.
                        SpansExpansionRaw expFirst = (SpansExpansionRaw)sp1;
                        SpansExpansionRaw expSecond = (SpansExpansionRaw)sp2;
                        // Note that the first clause is startpoint-sorted
                        BLSpans gapped = new SpansSequenceWithGap(ensureSorted(first, expFirst.clause()),
                                expFirst.gap(), ensureSorted(second, expSecond.clause()));
                        newSpans = new SpansExpansionRaw(expSecond.lengthGetter(), gapped,
                                Direction.RIGHT, expSecond.gap().minSize(), expSecond.gap().maxSize());
                    } else {
                        // Only first is a forward expansion.
                        SpansExpansionRaw expFirst = (SpansExpansionRaw)sp1;
                        newSpans = new SpansSequenceWithGap(ensureSorted(first, expFirst.clause()),
                                expFirst.gap(), ensureSorted(second, sp2));
                    }
                    i--;
                    replaceCombiParts(parts, i, first, second, newSpans);
                } else if (sp2 instanceof SpansExpansionRaw && ((SpansExpansionRaw)sp2).direction() == Direction.LEFT) {
                    // Second is a backward expansion (much less common, but can probably occur sometimes)
                    BLSpans newSpans;
                    if (sp1 instanceof SpansExpansionRaw && ((SpansExpansionRaw)sp1).direction() == Direction.LEFT) {
                        // First is a backward expansion too. Make the whole resulting clause a backward expansion
                        //   instead, so we can repeat the sequence-with-gaps trick.
                        SpansExpansionRaw expFirst = (SpansExpansionRaw)sp1;
                        SpansExpansionRaw expSecond = (SpansExpansionRaw)sp2;
                        BLSpans gapped = new SpansSequenceWithGap(ensureSorted(first, expFirst.clause()),
                                expSecond.gap(), ensureSorted(second, expSecond.clause()));
                        newSpans = new SpansExpansionRaw(expFirst.lengthGetter(), gapped,
                                Direction.LEFT, expFirst.gap().minSize(), expFirst.gap().maxSize());
                    } else {
                        // Only second is a backward expansion
                        SpansExpansionRaw expSecond = (SpansExpansionRaw)sp2;
                        newSpans = new SpansSequenceWithGap(ensureSorted(first, sp1), expSecond.gap(),
                                ensureSorted(second, expSecond.clause()));
                    }
                    i--;
                    replaceCombiParts(parts, i, first, second, newSpans);
                }
            }

            // Now, combine the rest (if any) using the more expensive SpansSequenceWithGap,
            // that takes more complex sequences into account.
            while (parts.size() > 1) {
                CombiPart first = parts.get(0);
                CombiPart second = parts.get(1);

                // Note: the spans coming from SpansSequenceWithGap may not be sorted by end point.
                // We keep track of this and sort them manually if necessary.
                BLSpans newSpans = new SpansSequenceWithGap(ensureSorted(first, first.spans), SequenceGap.NONE,
                        ensureSorted(second, second.spans));
                replaceCombiParts(parts, 0, first, second, newSpans);
            }

            return parts.get(0).spans;
        }

        private void replaceCombiParts(List<CombiPart> parts, int partIndex, CombiPart first, CombiPart second,
                BLSpans newSpans) {
            CombiPart newPart = new CombiPart(newSpans,
                    first.uniqueStart && first.uniqueEnd && second.uniqueStart,
                    first.uniqueEnd && second.uniqueStart && second.uniqueEnd,
                    true, first.endSorted && second.sameLength,
                    first.sameLength && second.sameLength);
            parts.remove(partIndex);
            parts.set(partIndex, newPart);
        }

        private BLSpans ensureSorted(CombiPart part, BLSpans spans) {
            if (!part.startSorted)
                return PerDocumentSortedSpans.startPoint(spans);
            return spans;
        }

    }

    @Override
    public String toString(String field) {
        return "SEQ(" + clausesToString(field) + ")";
    }

    @Override
    public boolean hitsAllSameLength() {
        for (BLSpanQuery clause : clauses) {
            if (!clause.hitsAllSameLength())
                return false;
        }
        return true;
    }

    @Override
    public int hitsLengthMin() {
        int n = 0;
        for (BLSpanQuery clause : clauses) {
            n += clause.hitsLengthMin();
        }
        return n;
    }

    @Override
    public int hitsLengthMax() {
        int n = 0;
        for (BLSpanQuery clause : clauses) {
            int max = clause.hitsLengthMax();
            if (max == Integer.MAX_VALUE)
                return max; // infinite
            n += max;
        }
        return n;
    }

    @Override
    public boolean hitsEndPointSorted() {
        return hitsStartPointSorted() && hitsAllSameLength();
    }

    @Override
    public boolean hitsStartPointSorted() {
        // Both SpansSequenceSimple and SpansSequenceWithGaps guarantee this
        return true;
    }

    @Override
    public boolean hitsHaveUniqueStart() {
        for (BLSpanQuery clause : clauses) {
            if (!clause.hitsHaveUniqueStart())
                return false;
        }
        return true;
    }

    @Override
    public boolean hitsHaveUniqueEnd() {
        for (BLSpanQuery clause : clauses) {
            if (!clause.hitsHaveUniqueEnd())
                return false;
        }
        return true;

    }

    @Override
    public boolean hitsAreUnique() {
        return hitsHaveUniqueStart() || hitsHaveUniqueEnd();
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        Nfa frag = null;
        int start = direction == SpanQueryFiSeq.DIR_TO_RIGHT ? 0 : clauses.size() - 1;
        int end = direction == SpanQueryFiSeq.DIR_TO_RIGHT ? clauses.size() : -1;
        for (int i = start; i != end; i += direction) {
            BLSpanQuery clause = clauses.get(i);
            Nfa clauseNfa = clause.getNfa(fiAccessor, direction);
            frag = frag == null ? clauseNfa : frag.append(clauseNfa);
        }
        return frag;
    }

    @Override
    public boolean canMakeNfa() {
        for (BLSpanQuery clause : clauses) {
            if (!clause.canMakeNfa())
                return false;
        }
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        long cost = Integer.MAX_VALUE;
        double factor = 1.0;
        for (BLSpanQuery clause : clauses) {
            cost = Math.min(cost, clause.reverseMatchingCost(reader));
            factor *= 1.2; // 20% overhead per clause (?)
        }
        return (long) (cost * factor);
    }

    @Override
    public int forwardMatchingCost() {
        int cost = 0;
        for (BLSpanQuery clause : clauses) {
            cost += clause.forwardMatchingCost();
        }
        return cost;
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean isFollowingClause) {
        // NOTE: we (explicitly) return false even though sequences can always
        // internalize neighbours, because sequences are explicitly flattened
        // while rewriting, so this shouldn't be necessary.
        // The internalize() method is used by other classes' internalize() methods, though.
        return false;
    }

    /**
     * Create a new sequence with a clause added to it.
     *
     * @param clause clause to add
     * @param addAtEnd if true, add at the end; if false, at the beginning
     * @return new sequence with clause added
     */
    @Override
    public SpanQuerySequence internalizeNeighbour(BLSpanQuery clause, boolean addAtEnd) {
        List<BLSpanQuery> cl = new ArrayList<>(clauses);
        if (addAtEnd)
            cl.add(clause);
        else
            cl.add(0, clause);
        return new SpanQuerySequence(cl);
    }

    /**
     * Either add a clause to an existing SpanQuerySequence, or create a new
     * SpanQuerySequence with the two specified clauses.
     *
     * @param whereToInternalize existing sequence, or existing non-sequence clause
     * @param clauseToInternalize clause to add to sequence or add to existing
     *            clause
     * @param addAtEnd if true, add new clause at the end of existing; if false,
     *            at the beginning
     * @return the expanded or newly created sequence
     */
    public static SpanQuerySequence sequenceInternalize(BLSpanQuery whereToInternalize, BLSpanQuery clauseToInternalize,
            boolean addAtEnd) {
        SpanQuerySequence seq;
        if (whereToInternalize instanceof SpanQuerySequence) {
            seq = (SpanQuerySequence) whereToInternalize;
            seq = seq.internalizeNeighbour(clauseToInternalize, addAtEnd);
        } else {
            if (addAtEnd)
                seq = new SpanQuerySequence(whereToInternalize, clauseToInternalize);
            else
                seq = new SpanQuerySequence(clauseToInternalize, whereToInternalize);
        }
        return seq;
    }

    // no hashCode() and equals() because super class version is sufficient


}
