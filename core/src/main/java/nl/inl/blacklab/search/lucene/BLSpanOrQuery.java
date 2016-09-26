package nl.inl.blacklab.search.lucene;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.DisiPriorityQueue;
import org.apache.lucene.search.DisiWrapper;
import org.apache.lucene.search.DisjunctionDISIApproximation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.ScoringWrapperSpans;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.PriorityQueue;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.NfaFragment;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.TokenPropMapper;

/** Matches the union of its clauses.
 */
public final class BLSpanOrQuery extends BLSpanQuery {

	SpanOrQuery inner;

	String field;

	private String luceneField;

	/** Construct a SpanOrQuery merging the provided clauses.
	 * All clauses must have the same field.
	 * @param clauses clauses to OR together
	 */
	public BLSpanOrQuery(BLSpanQuery... clauses) {
		inner = new SpanOrQuery(clauses);
		this.field = inner.getField();
		this.luceneField = clauses.length > 0 ? clauses[0].getRealField() : field;
	}

	static BLSpanOrQuery from(SpanOrQuery in) {
		SpanQuery[] clauses = in.getClauses();
		BLSpanQuery[] blClauses = new BLSpanQuery[clauses.length];
		for (int i = 0; i < clauses.length; i++) {
			blClauses[i] = BLSpansWrapper.blSpanQueryFrom(clauses[i]);
		}
		BLSpanOrQuery out = new BLSpanOrQuery(blClauses);
		return out;
	}

	/** Return the clauses whose spans are matched.
	 * @return the clauses
	 */
	public SpanQuery[] getClauses() {
		return (SpanQuery[])inner.getClauses();
	}

	/**
	 * Explicitly set the field for this query. Required because some queries
	 * rewrite to 0-clause or queries, and we need to be able to call getField()
	 * later.
	 *
	 * @param field
	 *            the field for this query
	 */
	public void setField(String field) {
		this.field = field;
	}

	@Override
	public String getField() {
		return field;
	}

	@Override
	public String getRealField() {
		return luceneField;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {

		// Flatten nested OR queries.
		// This doesn't change the query because the OR operator is associative.
		boolean anyRewritten = false;
		boolean hasNotChild = false;
		boolean allClausesSingleToken = true;
		List<BLSpanQuery> rewrittenCl = new ArrayList<>();
		for (SpanQuery ch: inner.getClauses()) {
			BLSpanQuery child = (BLSpanQuery)ch;
			BLSpanQuery rewritten = child.rewrite(reader);
			if (rewritten instanceof BLSpanOrQuery) {
				// Flatten.
				// Child OR operation we want to flatten into this OR operation.
				// Replace the child, incorporating its children into this OR operation.
				for (SpanQuery cl: ((BLSpanOrQuery)rewritten).getClauses()) {
					BLSpanQuery clause = (BLSpanQuery)cl;
					if (!clause.hitsAllSameLength() || clause.hitsLengthMax() != 1)
						allClausesSingleToken = false;
					rewrittenCl.add(clause);
				}
				anyRewritten = true;
			} else {
				if (rewritten.isSingleTokenNot())
					hasNotChild = true;
				// Just add it.
				rewrittenCl.add(rewritten);
				if (rewritten != child)
					anyRewritten = true;
				if (!rewritten.hitsAllSameLength() || rewritten.hitsLengthMax() != 1)
					allClausesSingleToken = false;
			}
		}

		// Rewrites OR queries containing some NOT children into "NAND" queries.
		// (only possible if all OR queries have token length 1!)
		// This helps us isolate problematic subclauses which we can then rewrite to
		// more efficient NOTCONTAINING clauses.
		if (hasNotChild && allClausesSingleToken) {
			// At least one clause starts with NOT.
			// Node should be rewritten to AND. Invert all clauses.
			for (int i = 0; i < rewrittenCl.size(); i++) {
				rewrittenCl.set(i, rewrittenCl.get(i).inverted());
			}
			// Note extra rewrite at the end to make sure AND NOT structure is correctly built.
			if (rewrittenCl.size() == 1)
				return rewrittenCl.get(0).inverted();
			return (new SpanQueryAnd(rewrittenCl).inverted()).rewrite(reader);
		}

		if (anyRewritten) {
			// Some clauses were rewritten.
			if (rewrittenCl.size() == 1)
				return rewrittenCl.get(0);
			return new BLSpanOrQuery(rewrittenCl.toArray(new BLSpanQuery[0]));
		}

		// Node need not be rewritten; return as-is
		return this;
	}

	@Override
	public boolean matchesEmptySequence() {
		for (SpanQuery cl: getClauses()) {
			BLSpanQuery clause = (BLSpanQuery)cl;
			if (clause.matchesEmptySequence())
				return true;
		}
		return false;
	}

	@Override
	public BLSpanQuery noEmpty() {
		if (!matchesEmptySequence())
			return this;
		List<BLSpanQuery> newCl = new ArrayList<>();
		for (SpanQuery cl: inner.getClauses()) {
			newCl.add(((BLSpanQuery)cl).noEmpty());
		}
		return new BLSpanOrQuery(newCl.toArray(new BLSpanQuery[0]));
	}

	@Override
	public boolean hitsAllSameLength() {
		if (getClauses().length == 0)
			return true;
		int l = ((BLSpanQuery)getClauses()[0]).hitsLengthMin();
		for (SpanQuery cl: getClauses()) {
			BLSpanQuery clause = (BLSpanQuery)cl;
			if (!clause.hitsAllSameLength() || clause.hitsLengthMin() != l)
				return false;
		}
		return true;
	}

	@Override
	public int hitsLengthMin() {
		int n = Integer.MAX_VALUE;
		for (SpanQuery cl: getClauses()) {
			BLSpanQuery clause = (BLSpanQuery)cl;
			n = Math.min(n, clause.hitsLengthMin());
		}
		return n;
	}

	@Override
	public int hitsLengthMax() {
		int n = 0;
		for (SpanQuery cl: getClauses()) {
			BLSpanQuery clause = (BLSpanQuery)cl;
			int l = clause.hitsLengthMax();
			if (l < 0)
				return -1; // infinite
			n = Math.max(n, l);
		}
		return n;
	}

	@Override
	public boolean hitsEndPointSorted() {
		// Cannot guarantee because we're merging from different sources.
		return false;
	}

	@Override
	public boolean hitsStartPointSorted() {
		// Our way of merging guarantees this, as it should for almost all BLSpans
		return true;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		// Cannot guarantee because we're merging from different sources.
		return false;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		// Cannot guarantee because we're merging from different sources.
		return false;
	}

	@Override
	public boolean hitsAreUnique() {
		// Cannot guarantee because we're merging from different sources.
		return false;
	}

	@Override
	public String toString(String field) {
		return "OR(" + clausesToString(field, inner.getClauses()) + ")";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof BLSpanOrQuery) {
			return inner.equals(((BLSpanOrQuery)o).inner);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return inner.hashCode() ^ 0xB1ACC1AB;
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		List<BLSpanWeight> subWeights = new ArrayList<>(inner.getClauses().length);
		for (SpanQuery q: inner.getClauses()) {
			BLSpanWeight weight = ((BLSpanQuery)q).createWeight(searcher, false);
			subWeights.add(weight);
		}
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(subWeights.toArray(new SpanWeight[0])) : null;
		return new SpanOrWeight(searcher, contexts, subWeights);
	}

	public class SpanOrWeight extends BLSpanWeight {
		final List<BLSpanWeight> subWeights;

		public SpanOrWeight(IndexSearcher searcher, Map<Term, TermContext> terms, List<BLSpanWeight> subWeights) throws IOException {
			super(BLSpanOrQuery.this, searcher, terms);
			this.subWeights = subWeights;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			for (final BLSpanWeight w: subWeights) {
				w.extractTerms(terms);
			}
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			for (BLSpanWeight w: subWeights) {
				w.extractTermContexts(contexts);
			}
		}

		class SpanPositionQueue extends PriorityQueue<Spans> {
			SpanPositionQueue(int maxSize) {
				super(maxSize, false); // do not prepopulate
			}

			protected boolean lessThan(Spans s1, Spans s2) {
				int start1 = s1.startPosition();
				int start2 = s2.startPosition();
				return (start1 < start2) ? true : (start1 == start2) ? s1.endPosition() < s2.endPosition() : false;
			}
		}

		@Override
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {

			final ArrayList<Spans> subSpans = new ArrayList<>(inner.getClauses().length);

			for (SpanWeight w: subWeights) {
				Spans spans = w.getSpans(context, requiredPostings);
				if (spans != null) {
					subSpans.add(spans);
				}
			}

			if (subSpans.isEmpty()) {
				return null;
			} else if (subSpans.size() == 1) {
				return new BLSpansWrapper(new ScoringWrapperSpans(subSpans.get(0), getSimScorer(context)));
			}

			final DisiPriorityQueue byDocQueue = new DisiPriorityQueue(subSpans.size());
			for (Spans spans: subSpans) {
				byDocQueue.add(new DisiWrapper(spans));
			}

			final SpanPositionQueue byPositionQueue = new SpanPositionQueue(subSpans.size()); // when
																								// empty
																								// use
																								// -1

			return new BLSpans() {
				Spans topPositionSpans = null;

				@Override
				public int nextDoc() throws IOException {
					topPositionSpans = null;
					DisiWrapper topDocSpans = byDocQueue.top();
					int currentDoc = topDocSpans.doc;
					do {
						topDocSpans.doc = topDocSpans.iterator.nextDoc();
						topDocSpans = byDocQueue.updateTop();
					} while (topDocSpans.doc == currentDoc);
					return topDocSpans.doc;
				}

				@Override
				public int advance(int target) throws IOException {
					topPositionSpans = null;
					DisiWrapper topDocSpans = byDocQueue.top();
					do {
						topDocSpans.doc = topDocSpans.iterator.advance(target);
						topDocSpans = byDocQueue.updateTop();
					} while (topDocSpans.doc < target);
					return topDocSpans.doc;
				}

				@Override
				public int docID() {
					DisiWrapper topDocSpans = byDocQueue.top();
					return topDocSpans.doc;
				}

				@Override
				public TwoPhaseIterator asTwoPhaseIterator() {
					float sumMatchCost = 0; // See also DisjunctionScorer.asTwoPhaseIterator()
					long sumApproxCost = 0;

					for (DisiWrapper w: byDocQueue) {
						if (w.twoPhaseView != null) {
							long costWeight = (w.cost <= 1) ? 1 : w.cost;
							sumMatchCost += w.twoPhaseView.matchCost() * costWeight;
							sumApproxCost += costWeight;
						}
					}

					if (sumApproxCost == 0) { // no sub spans supports approximations
						computePositionsCost();
						return null;
					}

					final float matchCost = sumMatchCost / sumApproxCost;

					return new TwoPhaseIterator(new DisjunctionDISIApproximation(byDocQueue)) {
						@Override
						public boolean matches() throws IOException {
							return twoPhaseCurrentDocMatches();
						}

						@Override
						public float matchCost() {
							return matchCost;
						}
					};
				}

				float positionsCost = -1;

				void computePositionsCost() {
					float sumPositionsCost = 0;
					long sumCost = 0;
					for (DisiWrapper w: byDocQueue) {
						long costWeight = (w.cost <= 1) ? 1 : w.cost;
						sumPositionsCost += w.spans.positionsCost() * costWeight;
						sumCost += costWeight;
					}
					positionsCost = sumPositionsCost / sumCost;
				}

				@Override
				public float positionsCost() {
					// This may be called when asTwoPhaseIterator returned null,
					// which happens when none of the sub spans supports approximations.
					assert positionsCost > 0;
					return positionsCost;
				}

				int lastDocTwoPhaseMatched = -1;

				boolean twoPhaseCurrentDocMatches() throws IOException {
					DisiWrapper listAtCurrentDoc = byDocQueue.topList();
					// remove the head of the list as long as it does not match
					final int currentDoc = listAtCurrentDoc.doc;
					while (listAtCurrentDoc.twoPhaseView != null) {
						if (listAtCurrentDoc.twoPhaseView.matches()) {
							// use this spans for positions at current doc:
							listAtCurrentDoc.lastApproxMatchDoc = currentDoc;
							break;
						}
						// do not use this spans for positions at current doc:
						listAtCurrentDoc.lastApproxNonMatchDoc = currentDoc;
						listAtCurrentDoc = listAtCurrentDoc.next;
						if (listAtCurrentDoc == null) {
							return false;
						}
					}
					lastDocTwoPhaseMatched = currentDoc;
					topPositionSpans = null;
					return true;
				}

				void fillPositionQueue() throws IOException { // called at first nextStartPosition
					assert byPositionQueue.size() == 0;
					// add all matching Spans at current doc to byPositionQueue
					DisiWrapper listAtCurrentDoc = byDocQueue.topList();
					while (listAtCurrentDoc != null) {
						Spans spansAtDoc = listAtCurrentDoc.spans;
						if (lastDocTwoPhaseMatched == listAtCurrentDoc.doc) { // matched by
																				// DisjunctionDisiApproximation
							if (listAtCurrentDoc.twoPhaseView != null) { // matched by approximation
								if (listAtCurrentDoc.lastApproxNonMatchDoc == listAtCurrentDoc.doc) { // matches()
																										// returned
																										// false
									spansAtDoc = null;
								} else {
									if (listAtCurrentDoc.lastApproxMatchDoc != listAtCurrentDoc.doc) {
										if (!listAtCurrentDoc.twoPhaseView.matches()) {
											spansAtDoc = null;
										}
									}
								}
							}
						}

						if (spansAtDoc != null) {
							assert spansAtDoc.docID() == listAtCurrentDoc.doc;
							assert spansAtDoc.startPosition() == -1;
							spansAtDoc.nextStartPosition();
							assert spansAtDoc.startPosition() != NO_MORE_POSITIONS;
							byPositionQueue.add(spansAtDoc);
						}
						listAtCurrentDoc = listAtCurrentDoc.next;
					}
					assert byPositionQueue.size() > 0;
				}

				@Override
				public int nextStartPosition() throws IOException {
					if (topPositionSpans == null) {
						byPositionQueue.clear();
						fillPositionQueue(); // fills byPositionQueue at first position
						topPositionSpans = byPositionQueue.top();
					} else {
						topPositionSpans.nextStartPosition();
						topPositionSpans = byPositionQueue.updateTop();
					}
					return topPositionSpans.startPosition();
				}

				@Override
				public int startPosition() {
					return topPositionSpans == null ? -1 : topPositionSpans.startPosition();
				}

				@Override
				public int endPosition() {
					return topPositionSpans == null ? -1 : topPositionSpans.endPosition();
				}

				@Override
				public int width() {
					return topPositionSpans.width();
				}

				@Override
				public void collect(SpanCollector collector) throws IOException {
					topPositionSpans.collect(collector);
				}

				@Override
				public String toString() {
					return "BLspanOr(" + BLSpanOrQuery.this + ")@" + docID() + ": " + startPosition() + " - " + endPosition();
				}

				long cost = -1;

				@Override
				public long cost() {
					if (cost == -1) {
						cost = 0;
						for (Spans spans: subSpans) {
							cost += spans.cost();
						}
					}
					return cost;
				}

				// BL

				@Override
				public void passHitQueryContextToClauses(HitQueryContext theContext) {
					for (Spans spans : subSpans) {
						((BLSpans)spans).setHitQueryContext(theContext);
					}
				}

				@Override
				public void getCapturedGroups(Span[] capturedGroups) {
					if (!childClausesCaptureGroups)
						return;
					((BLSpans) topPositionSpans).getCapturedGroups(capturedGroups);
				}
			};
		}
	}

	@Override
	public NfaFragment getNfa(TokenPropMapper propMapper, int direction) {
		List<NfaState> states = new ArrayList<>();
		List<NfaState> dangling = new ArrayList<>();
		for (SpanQuery cl: getClauses()) {
			BLSpanQuery clause = (BLSpanQuery)cl;
			NfaFragment frag = clause.getNfa(propMapper, direction);
			states.add(frag.getStartingState());
			dangling.addAll(frag.getDanglingArrows());
		}
		return new NfaFragment(NfaState.or(states), dangling);
	}

	@Override
	public boolean canMakeNfa() {
		for (SpanQuery cl: getClauses()) {
			BLSpanQuery clause = (BLSpanQuery)cl;
			if (!clause.canMakeNfa())
				return false;
		}
		return true;
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		// Add the costs of our clauses, since we won't
		// be able to skip any hits.
		int cost = 0;
		for (SpanQuery cl: getClauses()) {
			BLSpanQuery clause = (BLSpanQuery)cl;
			cost += clause.estimatedNumberOfHits(reader);
		}
		return cost;
	}

}
