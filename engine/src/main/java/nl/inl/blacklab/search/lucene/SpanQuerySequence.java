/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;
import nl.inl.blacklab.search.lucene.SpansSequenceWithGap.Gap;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombiner;

/**
 * Combines spans, keeping only combinations of hits that occur one after the
 * other. The order is significant: a hit from the first span must be followed
 * by a hit from the second.
 *
 * Note that this class is different from
 * org.apache.lucene.search.spans.SpanNearQuery: it tries to make sure it
 * generates *all* possible sequence matches. SpanNearQuery doesn't do this;
 * once a hit is used in a SpanNearQuery match, it advances to the next hit.
 *
 * In the future, this class could be expanded to make the exact behaviour
 * configurable: find all matches / find longest matches / find shortest matches
 * / ...
 *
 * See SpanSequenceRaw for details on the matching process.
 */
public class SpanQuerySequence extends BLSpanQueryAbstract {
    protected static final Logger logger = LogManager.getLogger(SpanQuerySequence.class);

    private static final boolean USE_SPANS_SEQUENCE_GAPS = true;

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
     *
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
                anyRewritten = true;
            }
        }
        return anyRewritten;
    }

    /**
     * Try to match separate start and end tags in this sequence, and convert into a
     * position filter (e.g. containing) query.
     *
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

    private static String ord(int pass) {
        pass++;
        switch(pass) {
        case 1:
            return "1st";
        case 2:
            return "2nd";
        case 3:
            return "3rd";
        default:
            return pass + "th";
        }
    }

    private static String prioName(int prio) {
        return prio == ClauseCombiner.CANNOT_COMBINE ? "CANNOT_COMBINE" : Integer.toString(prio);
    }

    static boolean combineAdjacentClauses(List<BLSpanQuery> cl, IndexReader reader, String fieldName,
            Set<ClauseCombiner> combiners) {

        boolean anyRewritten = false;

        // Rewrite adjacent clauses according to rewriting precedence rules
        boolean anyRewrittenThisCycle = true;
        int pass = 0;
        BLSpanQuery searchLogger = !cl.isEmpty() && BlackLabIndexImpl.traceOptimization() ? cl.get(0) : null;
        if (searchLogger != null)
            searchLogger.log(LogLevel.OPT, "SpanQuerySequence.combineAdjacentClauses() start");
        while (anyRewrittenThisCycle) {
            if (searchLogger != null)
                searchLogger.log(LogLevel.OPT, "Clauses before " + ord(pass) + " pass: " + StringUtils.join(cl, ", "));
            pass++;

            anyRewrittenThisCycle = false;

            // Find the highest-priority rewrite possible
            int highestPrio = ClauseCombiner.CANNOT_COMBINE, highestPrioIndex = -1;
            ClauseCombiner highestPrioCombiner = null;
            BLSpanQuery left, right;
            for (int i = 1; i < cl.size(); i++) {
                // See if any combiners apply, and if the priority is higher than found so far.
                left = cl.get(i - 1);
                right = cl.get(i);
                for (ClauseCombiner combiner : combiners) {
                    int prio = combiner.priority(left, right, reader);
                    if (searchLogger != null) {
                        if (prio == ClauseCombiner.CANNOT_COMBINE)
                            searchLogger.log(LogLevel.CHATTY, "(Cannot apply " + combiner + "(" + left + ", " + right + "))");
                        else
                            searchLogger.log(LogLevel.DETAIL, "Can apply " + combiner + "(" + left + ", " + right + "), priority: " + prioName(prio));
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
                if (searchLogger != null) {
                    left = cl.get(highestPrioIndex - 1);
                    right = cl.get(highestPrioIndex);
                    searchLogger.log(LogLevel.OPT, "Execute lowest prio number combiner: " + highestPrioCombiner + "(" + left + ", " + right + ")");
                }
                left = cl.get(highestPrioIndex - 1);
                right = cl.get(highestPrioIndex);
                BLSpanQuery combined = highestPrioCombiner.combine(left, right, reader);
                // (we used to rewrite() combined here just to be safe, but that could break optimizations later)
                cl.remove(highestPrioIndex);
                cl.set(highestPrioIndex - 1, combined);
                anyRewrittenThisCycle = true;
            }
            if (anyRewrittenThisCycle)
                anyRewritten = true;
        }
        if (searchLogger != null)
            searchLogger.log(LogLevel.OPT, "Cannot combine any other clauses. Result: " + StringUtils.join(cl, ", "));

        return anyRewritten;
    }

    @Override
    public BLSpanQuery optimize(IndexReader reader) throws IOException {
        super.optimize(reader);
        BlackLabIndex index = BlackLab.fromIndexReader(reader);
        boolean canDoNfaMatching = false;
        if (index instanceof BlackLabIndexImpl) {
            canDoNfaMatching = ((BlackLabIndexImpl)index).canDoNfaMatching();
        }
        boolean anyRewritten = false;

        // Make a copy, because our methods rewrite things in-place.
        List<BLSpanQuery> cl = new ArrayList<>(clauses);

        // Flatten nested sequences.
        // This doesn't change the query because the sequence operator is associative.
        anyRewritten |= flattenSequence(cl);

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
        anyRewritten |= combineAdjacentClauses(cl, reader, getField(), ClauseCombiner.all(canDoNfaMatching));

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

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BlackLabIndex index = BlackLab.fromIndexReader(reader);
        boolean canDoNfaMatching = false;
        if (index instanceof BlackLabIndexImpl) {
            canDoNfaMatching = ((BlackLabIndexImpl)index).canDoNfaMatching();
        }
        boolean anyRewritten = false;

        // Make a copy, because our methods rewrite things in-place.
        List<BLSpanQuery> cl = new ArrayList<>(clauses);

        // Flatten nested sequences.
        // This doesn't change the query because the sequence operator is associative.
        anyRewritten |= flattenSequence(cl);

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
        anyRewritten |= combineAdjacentClauses(cl, reader, getField(), ClauseCombiner.all(canDoNfaMatching));

        // Rewrite each clause, and flatten again if necessary
        anyRewritten |= rewriteClauses(cl, reader);
        if (anyRewritten)
            flattenSequence(cl);

        // Again, try to combine adjacent clauses into more efficient ones. Rewriting clauses may
        // have
        // generated new opportunities for combining clauses.
        anyRewritten |= combineAdjacentClauses(cl, reader, getField(), ClauseCombiner.all(canDoNfaMatching));

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

    /**
     * For possibly empty clauses, combine them with a neighbour into a binary-tree
     * structure. This differs from the approach of makeAlternatives() which
     * produces a OR of several longer sequences. That approach is probably more
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
                BLSpanQuery left = cl.get(i - 1);
                BLSpanQuery right = cl.get(i);
                boolean leftEmpty = left.matchesEmptySequence();
                boolean rightEmpty = right.matchesEmptySequence();
                // Does either clause matcht the empty sequence, and are these two
                // the best candidates to combine right now?
                if ((leftEmpty || rightEmpty) && bestBothEmpty || (!bestBothEmpty && (!leftEmpty || !rightEmpty))) {
                    bestBothEmpty = leftEmpty && rightEmpty;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0)
                return anyRewritten; // we're done
            // Combine the clauses we found
            BLSpanQuery left = cl.get(bestIndex - 1);
            BLSpanQuery right = cl.get(bestIndex);
            boolean leftEmpty = left.matchesEmptySequence();
            boolean rightEmpty = right.matchesEmptySequence();
            BLSpanQuery combi;
            BLSpanQuery both = new SpanQuerySequence(left, right);
            if (leftEmpty && rightEmpty) {
                // 4 alternatives: neither, left only, right only, or both
                combi = new SpanQueryRepetition(new BLSpanOrQuery(left, right, both), 0, 1);
            } else if (leftEmpty) {
                // 2 alternatives: right only, or both
                combi = new BLSpanOrQuery(right, both);
            } else {
                // 2 alternatives: left only, or both
                combi = new BLSpanOrQuery(left, both);
            }
            cl.remove(bestIndex - 1);
            cl.set(bestIndex - 1, combi);
            anyRewritten = true;
        }
    }

    /**
     * Given translated clauses, builds several alternatives and combines them with
     * OR.
     *
     * This is necessary because of how sequence matching works: first the hits in
     * each of the clauses are located, then we try to detect valid sequences by
     * looking at these hits. But when a clause also matches the empty sequence, you
     * may miss valid sequence matches because there's no hit in the clause to
     * combine with the hits from other clauses.
     *
     * @param parts translation results for each of the clauses so far
     * @param reader the index reader
     * @return several alternatives combined with or
     * @throws IOException
     */
    List<List<BLSpanQuery>> makeAlternatives(List<BLSpanQuery> parts, IndexReader reader) throws IOException {
        if (parts.size() == 1) {
            // Last clause in the sequence; just return it
            // (noEmpty() version because we will build alternatives
            // in the caller if the input matched the empty sequence)
            return Arrays.asList(Arrays.asList(parts.get(0).noEmpty().rewrite(reader)));
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
            results.add(Arrays.asList(headNoEmpty));
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
    public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        List<BLSpanWeight> weights = new ArrayList<>();
        for (BLSpanQuery clause : clauses) {
            weights.add(clause.createWeight(searcher, needsScores));
        }
        Map<Term, TermContext> contexts = needsScores ? getTermContexts(weights.toArray(new SpanWeight[0])) : null;
        return new SpanWeightSequence(weights, searcher, contexts);
    }

    class SpanWeightSequence extends BLSpanWeight {

        final List<BLSpanWeight> weights;

        public SpanWeightSequence(List<BLSpanWeight> weights, IndexSearcher searcher, Map<Term, TermContext> terms)
                throws IOException {
            super(SpanQuerySequence.this, searcher, terms);
            this.weights = weights;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            for (SpanWeight weight : weights) {
                weight.extractTerms(terms);
            }
        }

        @Override
        public void extractTermContexts(Map<Term, TermContext> contexts) {
            for (SpanWeight weight : weights) {
                weight.extractTermContexts(contexts);
            }
        }

        class CombiPart {
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
            for (int i = 0; i < weights.size(); i++) {
                CombiPart part = new CombiPart(weights.get(i), context, requiredPostings);
                if (part.spans == null)
                    return null;
                parts.add(part);
            }

            // First, combine as many clauses as possible into SpansSequenceSimple,
            // which works for simple clauses and is the most efficient to execute.
            // OPT: it might be even better to favour combining low-frequency terms first,
            // as that minimizes useless skipping through non-matching docs.
            for (int i = 1; i < parts.size(); i++) {
                CombiPart left = parts.get(i - 1);
                CombiPart right = parts.get(i);
                CombiPart newPart = null;
                if (left.uniqueEnd && left.endSorted && right.startSorted && right.uniqueStart) {
                    // We can take a shortcut because of what we know about the Spans we're
                    // combining.
                    SpansSequenceSimple newSpans = new SpansSequenceSimple(left.spans, right.spans);
                    newPart = new CombiPart(newSpans, left.uniqueStart, right.uniqueEnd, left.startSorted,
                            right.sameLength,
                            left.sameLength && right.sameLength);
                    parts.remove(i - 1);
                    parts.set(i - 1, newPart);
                    i--;
                }
            }

            // Next, see if we have SpansExpansion that we can resolve using SpansSequenceWithGap.
            for (int i = 1; i < parts.size(); i++) {
                CombiPart left = parts.get(i - 1);
                CombiPart right = parts.get(i);
                CombiPart newPart = null;
                BLSpans lsp = left.spans;
                BLSpans rsp = right.spans;
                if (lsp instanceof SpansExpansionRaw && ((SpansExpansionRaw)lsp).direction() == Direction.RIGHT) {
                    // Left is an expand-to-right. Make a SpansSequenceWithGap.
                    BLSpans newSpans;
                    if (rsp instanceof SpansExpansionRaw && ((SpansExpansionRaw)rsp).direction() == Direction.RIGHT) {
                        // Right is an expansion-to-the-right too. Make the whole resulting clause expansion-to-right
                        //   instead, so we can repeat the sequence-with-gaps trick.
                        SpansExpansionRaw expLeft = (SpansExpansionRaw)lsp;
                        SpansExpansionRaw expRight = (SpansExpansionRaw)rsp;
                        BLSpans gapped = new SpansSequenceWithGap(expLeft.clause(), expLeft.gap(), expRight.clause());
                        newSpans = new SpansExpansionRaw(expRight.lengthGetter(), gapped, Direction.RIGHT, expRight.gap().minSize(), expRight.gap().maxSize());
                    } else {
                        // Only left is an expansion-to-the-right.
                        SpansExpansionRaw expLeft = (SpansExpansionRaw)lsp;
                        newSpans = new SpansSequenceWithGap(expLeft.clause(), expLeft.gap(), rsp);
                    }
                    newPart = new CombiPart(newSpans, left.uniqueStart && left.uniqueEnd && right.uniqueStart,
                            left.uniqueEnd && right.uniqueStart && right.uniqueEnd, left.startSorted, right.sameLength,
                            left.sameLength && right.sameLength);
                    parts.remove(i - 1);
                    parts.set(i - 1, newPart);
                    i--;
                } else if (rsp instanceof SpansExpansionRaw && ((SpansExpansionRaw)rsp).direction() == Direction.LEFT) {
                    // Right is an expand-to-left (much less common, but can probably occur sometimes)
                    BLSpans newSpans;
                    if (lsp instanceof SpansExpansionRaw && ((SpansExpansionRaw)lsp).direction() == Direction.LEFT) {
                        // Left is an expansion-to-the-left too. Make the whole resulting clause expansion-to-left
                        //   instead, so we can repeat the sequence-with-gaps trick.
                        SpansExpansionRaw expLeft = (SpansExpansionRaw)lsp;
                        SpansExpansionRaw expRight = (SpansExpansionRaw)rsp;
                        BLSpans gapped = new SpansSequenceWithGap(expLeft.clause(), expRight.gap(), expRight.clause());
                        newSpans = new SpansExpansionRaw(expLeft.lengthGetter(), gapped, Direction.LEFT, expLeft.gap().minSize(), expLeft.gap().maxSize());
                    } else {
                        // Only right is an expasion-to-the-left
                        SpansExpansionRaw expRight = (SpansExpansionRaw)rsp;
                        newSpans = new SpansSequenceWithGap(lsp, expRight.gap(), expRight.clause());
                    }
                    newPart = new CombiPart(newSpans, left.uniqueStart && left.uniqueEnd && right.uniqueStart,
                            left.uniqueEnd && right.uniqueStart && right.uniqueEnd, left.startSorted, right.sameLength,
                            left.sameLength && right.sameLength);
                    parts.remove(i - 1);
                    parts.set(i - 1, newPart);
                    i--;
                }
            }

            // Now, combine the rest (if any) using the more expensive SpansSequenceRaw,
            // that takes more complex sequences into account.
            while (parts.size() > 1) {
                CombiPart left = parts.get(0);
                CombiPart right = parts.get(1);

                if (USE_SPANS_SEQUENCE_GAPS) {
                    // Note: the spans coming from SpansSequenceWithGap may not be sorted by end point.
                    // We keep track of this and sort them manually if necessary.
                    CombiPart newPart = null;
                    if (!left.endSorted)
                        left.spans = PerDocumentSortedSpans.endPoint(left.spans);
                    if (!right.startSorted)
                        right.spans = PerDocumentSortedSpans.startPoint(right.spans);
                    BLSpans newSpans = new SpansSequenceWithGap(left.spans, Gap.NONE, right.spans);
                    newPart = new CombiPart(newSpans, left.uniqueStart && left.uniqueEnd && right.uniqueStart,
                            left.uniqueEnd && right.uniqueStart && right.uniqueEnd, left.startSorted, right.sameLength,
                            left.sameLength && right.sameLength);
                    parts.remove(0);
                    parts.set(0, newPart);
                } else {
                    // Note: the spans coming from SequenceSpansRaw may not be sorted by end point.
                    // We keep track of this and sort them manually if necessary.
                    CombiPart newPart = null;
                    if (!left.endSorted)
                        left.spans = PerDocumentSortedSpans.endPoint(left.spans);
                    if (!right.startSorted)
                        right.spans = PerDocumentSortedSpans.startPoint(right.spans);
                    BLSpans newSpans = new SpansSequenceRaw(left.spans, right.spans);
                    newPart = new CombiPart(newSpans, left.uniqueStart && left.uniqueEnd && right.uniqueStart,
                            left.uniqueEnd && right.uniqueStart && right.uniqueEnd, left.startSorted, right.sameLength,
                            left.sameLength && right.sameLength);
                    parts.remove(0);
                    parts.set(0, newPart);
                }
            }

            return parts.get(0).spans;
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
        for (int i = 0; i < clauses.size() - 1; i++) {
            if (!clauses.get(i).hitsHaveUniqueEnd())
                return false;
        }
        for (int i = 1; i < clauses.size(); i++) {
            if (!clauses.get(i).hitsAllSameLength())
                return false;
        }
        return true;
    }

    @Override
    public boolean hitsStartPointSorted() {
        for (int i = 0; i < clauses.size() - 1; i++) {
            if (!clauses.get(i).hitsAllSameLength())
                return false;
        }
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
            if (frag == null)
                frag = clause.getNfa(fiAccessor, direction);
            else
                frag.append(clause.getNfa(fiAccessor, direction));
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
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
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
     * @param addToRight if true, add to the right; if false, to the left
     * @return new sequence with clause added
     */
    @Override
    public SpanQuerySequence internalizeNeighbour(BLSpanQuery clause, boolean addToRight) {
        List<BLSpanQuery> cl = new ArrayList<>(clauses);
        if (addToRight)
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
     * @param addToRight if true, add new clause to the right of existing; if false,
     *            to the left
     * @return the expanded or newly created sequence
     */
    public static SpanQuerySequence sequenceInternalize(BLSpanQuery whereToInternalize, BLSpanQuery clauseToInternalize,
            boolean addToRight) {
        SpanQuerySequence seq;
        if (whereToInternalize instanceof SpanQuerySequence) {
            seq = (SpanQuerySequence) whereToInternalize;
            seq = seq.internalizeNeighbour(clauseToInternalize, addToRight);
        } else {
            if (addToRight)
                seq = new SpanQuerySequence(whereToInternalize, clauseToInternalize);
            else
                seq = new SpanQuerySequence(clauseToInternalize, whereToInternalize);
        }
        return seq;
    }

    // no hashCode() and equals() because super class version is sufficient


}
