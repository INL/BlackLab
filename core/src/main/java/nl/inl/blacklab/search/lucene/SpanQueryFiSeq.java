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
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;

/**
 * Find hits that match the specified NFA, starting from the
 * specified anchor query (either the start of end of those hits,
 * with a possible offset added), in the specified direction.
 */
public class SpanQueryFiSeq extends BLSpanQueryAbstract {

	NfaState nfa;

	int direction;

	boolean startOfAnchor;

	ForwardIndexAccessor fiAccessor;

	/**
	 *
	 * @param anchor hits to use as anchor to start NFA matching
	 * @param startOfAnchor if true, use the starts of anchor hits; if false, use the ends
	 * @param nfa the NFA to use for matching
	 * @param direction the direction to match in (-1 = right-to-left, 1 = left-to-right)
	 * @param fiAccessor maps between term strings and term indices for each property
	 */
	public SpanQueryFiSeq(BLSpanQuery anchor, boolean startOfAnchor, NfaState nfa, int direction, ForwardIndexAccessor fiAccessor) {
		super(anchor);
		this.nfa = nfa;
		this.startOfAnchor = startOfAnchor;
		this.direction = direction;
		this.fiAccessor = fiAccessor;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery rewritten = clauses.get(0).rewrite(reader);
		if (rewritten != clauses.get(0)) {
			SpanQueryFiSeq result = new SpanQueryFiSeq(rewritten, startOfAnchor, nfa, direction, fiAccessor);
			return result;
		}
		return this;
	}

	// @Override
	// public BLSpanQuery combineWithPrecedingPart(BLSpanQuery previousPart, IndexReader reader)
	// throws IOException {
	// BLSpanQuery result = super.combineWithPrecedingPart(previousPart, reader);
	// if (result == null && previousPart.hitsAllSameLength()) {
	// // We "gobble up" the previous part and adjust our left matching edge.
	// // This should make filtering more efficient, since we will likely have fewer hits to filter.
	// SpanQueryFiSeq r = (SpanQueryFiSeq)copy();
	// r.clauses.set(0, new SpanQuerySequence(previousPart, clauses.get(0)));
	// r.adjustLeft(previousPart.hitsLengthMin());
	// result = r;
	// }
	// return result;
	// }

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		BLSpanWeight anchorWeight = clauses.get(0).createWeight(searcher, needsScores);
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(anchorWeight) : null;
		return new SpanWeightFiSeq(anchorWeight, searcher, contexts);
	}

	public class SpanWeightFiSeq extends BLSpanWeight {

		final BLSpanWeight anchorWeight;

		public SpanWeightFiSeq(BLSpanWeight anchorWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryFiSeq.this, searcher, terms);
			this.anchorWeight = anchorWeight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			anchorWeight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			anchorWeight.extractTermContexts(contexts);
		}

		@Override
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans anchorSpans = anchorWeight.getSpans(context, requiredPostings);
			if (anchorSpans == null)
				return null;
			// @@@ make sure anchor hits are unique?
			return new SpansFiSeq(anchorSpans, startOfAnchor, nfa, direction, fiAccessor.getForwardIndexAccessorLeafReader(context.reader()));
		}
	}

	@Override
	public String toString(String field) {
		return "FISEQ(" + clausesToString(field) + ", " + nfa + ", " + direction + ")";
	}

	public SpanQueryFiSeq copy() {
		return new SpanQueryFiSeq(clauses.get(0), startOfAnchor, nfa, direction, fiAccessor);
	}

	@Override
	public boolean matchesEmptySequence() {
		return false; // can't be used if clause matches empty sequence, we need anchors
	}

	@Override
	public BLSpanQuery noEmpty() {
		return this;
	}

	@Override
	public boolean hitsAllSameLength() {
		return clauses.get(0).hitsAllSameLength() && nfa.hitsAllSameLength(NfaState.emptySet());
	}

	@Override
	public int hitsLengthMin() {
		if (startOfAnchor && direction == -1 || !startOfAnchor && direction == 1) {
			// Non-overlapping; add the two values
			return clauses.get(0).hitsLengthMin() + nfa.hitsLengthMin(NfaState.emptySet());
		}
		// Overlapping; use the largest value
		return Math.max(clauses.get(0).hitsLengthMin(), nfa.hitsLengthMin(NfaState.emptySet()));
	}

	@Override
	public int hitsLengthMax() {
		if (startOfAnchor && direction == -1 || !startOfAnchor && direction == 1) {
			// Non-overlapping; add the two values
			return clauses.get(0).hitsLengthMax() + nfa.hitsLengthMax(NfaState.emptySet());
		}
		// Overlapping; use the largest value
		return Math.min(clauses.get(0).hitsLengthMax(), nfa.hitsLengthMax(NfaState.emptySet()));
	}

	@Override
	public boolean hitsStartPointSorted() {
		if (direction == 1)
			return clauses.get(0).hitsStartPointSorted();
		return false; // can't easily guarantee this because we're matching right-to-left
	}

	@Override
	public boolean hitsEndPointSorted() {
		if (direction == -1)
			return clauses.get(0).hitsEndPointSorted();
		return false; // can't easily guarantee this because we're matching left-to-right
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return clauses.get(0).hitsHaveUniqueStart();
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return false;
	}

	@Override
	public boolean hitsAreUnique() {
		return clauses.get(0).hitsHaveUniqueStart() && direction == 1 || clauses.get(0).hitsHaveUniqueEnd() && direction == -1;
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		return clauses.get(0).estimatedNumberOfHits(reader);
	}
}
