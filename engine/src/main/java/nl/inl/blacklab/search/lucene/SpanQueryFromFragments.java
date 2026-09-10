package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.queries.spans.SpanCollector;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.indexers.config.Span;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Converts documents and fragments results into spans.
 * <p>
 * If the whole document matches, that's the only span that will be produced.
 * Adjacent fragments will be combined into a single span.
 */
public class SpanQueryFromFragments extends BLSpanQuery {

    /** Have we nexted the fragment query but not used the result yet? */
    private boolean fragmentQueryNexted = false;

    /** A query yielding full documents and/or fragments */
    private final Query fragmentQuery;

    /** Creates a bitset of full documents per segment, so we can find a fragment's parent */
    private final BitSetProducer fullDocsBitSetProducer;

    /** Field that gives us the document length in tokens */
    private final String tokenLengthField;

    public SpanQueryFromFragments(QueryInfo queryInfo, Query fragmentQuery) {
        super(queryInfo);
        this.fragmentQuery = fragmentQuery;
        this.tokenLengthField = queryInfo.field().tokenLengthField();

        fullDocsBitSetProducer = new QueryBitSetProducer(BLInputDocument.docTypeQuery(BLInputDocument.DocType.DOCUMENT));
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        Query frRewr = fragmentQuery.rewrite(reader);
        if (frRewr != fragmentQuery) {
            return new SpanQueryFromFragments(queryInfo, frRewr);
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        Weight fragmentWeight = fragmentQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 0);
        return new BLSpanWeight(this, searcher, null, 0) {
            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return fragmentWeight.isCacheable(ctx);
            }

            @Override
            public void extractTermStates(Map<Term, TermStates> contexts) {
                // No terms
            }

            @Override
            public BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
                Scorer fragmentScorer = fragmentWeight.scorer(ctx);
                if (fragmentScorer == null)
                    return null; // no matches in segment
                BitSet fullDocsBitSet = fullDocsBitSetProducer.getBitSet(ctx);
                return new FragmentsToSpans(fragmentScorer, fullDocsBitSet, ctx);
            }
        };
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0;
    }

    @Override
    public int forwardMatchingCost() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getRealField() {
        return queryInfo.field().mainAnnotation().mainSensitivity().luceneField();
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getRealField())) {
            fragmentQuery.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
        }
    }

    @Override
    public String toString(String field) {
        return "SpanQueryFromFragments(" + fragmentQuery.toString(field) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryFromFragments that = (SpanQueryFromFragments) o;
        return Objects.equals(fragmentQuery, that.fragmentQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fragmentQuery);
    }

    /** Get the spans matching the full document and fragment matches */
    private class FragmentsToSpans extends BLSpans {

        /** Iterator over the matched index documents (full documents and/or fragments) */
        private final DocIdSetIterator fragmentIterator;

        /** DocValues for _frag_annotatedField (field this is a fragment of) */
        private final SortedDocValues dvFragAnnotatedField;

        /** Ord of the annotated field we're searching. Only look at matching fragments in this field. */
        private final long currentAnnotatedFieldOrd;

        /** DocValues for _frag_start (start of fragment) */
        private final NumericDocValues dvFragStart;

        /** DocValues for _frag_end (start of fragment) */
        private final NumericDocValues dvFragEnd;

        /** BitSet indicating which Lucene docs are full documents (not fragments)
         * (needed to find the full document for a fragment if the full document wasn't matched already)
         */
        private final BitSet fullDocsBitSet;

        /** DocValues for token length field */
        private final NumericDocValues dvTokenLength;

        /** One greater than highest doc id */
        private final int maxDoc;

        /** Current matching document id (last returned from nextDoc) */
        private int currentDocId;

        /** Spans we're producing from this document. */
        private final List<Span> spansInCurrentDoc = new ArrayList<>();

        /** Spans we're producing from this document. */
        private Iterator<Span> spansIt;

        /** Span we're currently positioned at */
        private Span currentSpan;

        // Fragment defined by the current result from the fragmentIterator (may not be processed yet).
        int fragDocId;
        int fragStart;
        int fragEnd;
        boolean fragInCorrectField;
        boolean fragIsFullDoc;

        public FragmentsToSpans(Scorer fragmentScorer, BitSet fullDocsBitSet, LeafReaderContext ctx) {
            super(SpanGuarantees.SORTED_UNIQUE);
            try {
                // Get the DocValues for the fields we need to read from the fragmentIterator results
                LeafReader reader = ctx.reader();
                dvTokenLength = reader.getNumericDocValues(tokenLengthField);
                dvFragAnnotatedField = reader.getSortedDocValues(BLInputDocument.FRAG_FIELD_ANNOTATED_FIELD);
                String annotatedFieldName = queryInfo.field().name();
                currentAnnotatedFieldOrd = dvFragAnnotatedField.lookupTerm(new BytesRef(annotatedFieldName));
                dvFragStart = reader.getNumericDocValues(BLInputDocument.FRAG_FIELD_START);
                dvFragEnd = reader.getNumericDocValues(BLInputDocument.FRAG_FIELD_END);
                maxDoc = reader.maxDoc();
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
            fragmentIterator = fragmentScorer.iterator();
            this.fullDocsBitSet = fullDocsBitSet;
            currentDocId = -1;
        }

        @Override
        public int docID() {
            return currentDocId;
        }

        @Override
        public int nextDoc() throws IOException {
            if (fragmentIterator.docID() == NO_MORE_DOCS) {
                currentDocId = NO_MORE_DOCS;
                return NO_MORE_DOCS;
            }
            // Find the next full document we're returning spans from, and determine all the spans
            if (ensureAtMatchingFrag() == NO_MORE_DOCS)
                return NO_MORE_DOCS;

            return collectSpansInDoc();
        }

        /** Make sure we are at a full document or matching (i.e. in correct field) fragment.
         * <p>
         * Precondition: fragmentIterator is positioned at a full document or fragment.
         * Postcondition: fragmentIterator is positioned at a full document or matching fragment.
         * May be the same, could be different.
         *
         * @return the doc id of the full document we're returning spans from, or NO_MORE_DOCS if there are no more
         */
        private int ensureAtMatchingFrag() throws IOException {
            while (!fragmentQueryNexted || !fragInCorrectField) {
                fragmentQueryNexted = true;
                if (fragmentIterator.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                    currentDocId = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                // Determine the fragment (or full doc, i.e. fragment from 0 to end) fragmentIterator is currently at.
                determineFragment();
            }
            return fragDocId;
        }

        @Override
        public int advance(int target) throws IOException {
            if (target >= maxDoc) {
                currentDocId = NO_MORE_DOCS;
                return NO_MORE_DOCS;
            }

            // We need to advance fragmentIterator to the first fragment in a document >= target.
            // Find the previous full document before target, and advance to the next document after that,
            // which is the first fragment in a document >= target (or a full document without fragments).
            int firstFragId = target == 0 ? 0 : fullDocsBitSet.prevSetBit(target - 1) + 1;

            fragmentQueryNexted = true;
            if (fragmentIterator.advance(firstFragId) == DocIdSetIterator.NO_MORE_DOCS) {
                currentDocId = NO_MORE_DOCS;
                return NO_MORE_DOCS;
            }
            assert fragmentIterator.docID() >= firstFragId : "fragmentIterator.advance() returned a doc < firstFrag: " + fragmentIterator.docID() + " < " + firstFragId;
            determineFragment();
            if (ensureAtMatchingFrag() == NO_MORE_DOCS) // (skip any fragments in the wrong field)
                return NO_MORE_DOCS;
            int docId = collectSpansInDoc();
            assert docId >= target : "advance() returned a doc < target: " + docId + " < " + target;
            return docId;
        }

        @Override
        public int nextStartPosition() throws IOException {
            if (spansIt == null)
                return -1;
            if (!spansIt.hasNext()) {
                currentSpan = Span.between(NO_MORE_POSITIONS, NO_MORE_POSITIONS);
                return NO_MORE_POSITIONS;
            }
            currentSpan = spansIt.next();
            return currentSpan.start();
        }

        @Override
        public int startPosition() {
            if (currentSpan == null)
                return -1;
            return currentSpan.start();
        }

        @Override
        public int endPosition() {
            if (currentSpan == null)
                return -1;
            return currentSpan.end();
        }

        /**
         * Determine the fragment (or full document) fragmentIterator is currently at.
         * <p>
         * Precondition: fragmentIterator is positioned at a full document or fragment.
         * Sets fragDocId, fragStart, fragEnd, fragInCorrectField, fragIsFullDoc.
         */
        private void determineFragment() throws IOException {
            int docId = fragmentIterator.docID();
            if (fullDocsBitSet.get(docId)) {
                // This is a full document; yield the document
                fragIsFullDoc = true;
                fragInCorrectField = true; // (only applies to fragments)
                fragDocId = docId;
                fragStart = 0;
                if (dvTokenLength.docID() != docId)
                    dvTokenLength.advance(docId);
                fragEnd = (int)dvTokenLength.longValue();
            } else {
                // This is a fragment.
                if (dvFragAnnotatedField.docID() != docId) {
                    dvFragAnnotatedField.advance(docId);
                    dvFragStart.advance(docId);
                    dvFragEnd.advance(docId);
                }
                fragIsFullDoc = false;
                fragInCorrectField = dvFragAnnotatedField.ordValue() == currentAnnotatedFieldOrd;
                fragStart = (int)dvFragStart.longValue();
                fragEnd = (int)dvFragEnd.longValue();
                // Find the parent document (the next full doc in the index)
                fragDocId = fullDocsBitSet.nextSetBit(docId);
            }
        }

        /** Starting from a matching fragment, collect all fragments in this document.
         *
         * Precondition: fragmentIterator is positioned at a full document or matching fragment.
         * Postcondition: fragmentIterator is positioned at a full document or matching fragment in a new document
         * (or NO_MORE_DOCS).
         *
         * @return the doc id of the full document we're returning spans from, or NO_MORE_DOCS if there are no more
         */
        private int collectSpansInDoc() throws IOException {
            // We're now at the first fragment in a new document.
            // Collect this and all subsequent fragments in this doc as the spans we'll produce.
            currentDocId = fragDocId;
            spansInCurrentDoc.clear();
            // (we already know it's in the correct field, see ensureAtMatchingFrag())
            spansInCurrentDoc.add(Span.between(fragStart, fragEnd));
            while (true) {
                if (fragmentIterator.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                    // No more fragments left
                    break;
                }
                determineFragment();
                if (fragDocId != currentDocId) {
                    // This fragment is in a new document; we'll return it next time
                    break;
                }
                if (fragIsFullDoc) {
                    // This is a full document; yield the document and ignore any fragments in it
                    spansInCurrentDoc.clear();
                    spansInCurrentDoc.add(Span.between(fragStart, fragEnd));
                    // Note that full document is indexed last, so we could break here, but we continue to advance
                    // the fragmentIterator to the next match, which is expected by the rest of the code.
                } else {
                    // This is a fragment; add it to the list of spans, or merge with the previous span if adjacent
                    if (fragInCorrectField) {
                        addOrMergeSpan(spansInCurrentDoc, fragStart, fragEnd);
                    }
                }
            }
            spansIt = spansInCurrentDoc.iterator();
            return currentDocId;
        }

        /** Add a span to the list, combining with the previous span if adjacent. */
        private void addOrMergeSpan(List<Span> spansInCurrentDoc, int fragStart, int fragEnd) {
            if (!spansInCurrentDoc.isEmpty()) {
                Span lastSpan = spansInCurrentDoc.get(spansInCurrentDoc.size() - 1);
                if (lastSpan.end() == fragStart) {
                    // Adjacent fragment; combine with previous span
                    spansInCurrentDoc.set(spansInCurrentDoc.size() - 1, Span.between(lastSpan.start(), fragEnd));
                    return;
                }
            }
            spansInCurrentDoc.add(Span.between(fragStart, fragEnd));
        }

        @Override
        public long cost() {
            return fragmentIterator.cost();
        }

        @Override
        protected void passHitQueryContextToClauses(HitQueryContext context) {
            // No span clauses to pass context to
        }

        @Override
        public boolean hasMatchInfo() {
            return false;
        }

        @Override
        public void getMatchInfo(MatchInfo[] matchInfo) {
            // No match info to provide
        }

        @Override
        public RelationInfo getRelationInfo() {
            return null;
        }

        @Override
        public int width() {
            return 0;
        }

        @Override
        public void collect(SpanCollector collector) {
            // No spans to collect
        }

        @Override
        public float positionsCost() {
            return 0;
        }
    }
}
