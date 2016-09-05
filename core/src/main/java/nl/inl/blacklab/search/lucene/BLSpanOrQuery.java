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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.PriorityQueue;

import nl.inl.blacklab.search.Span;

/** Matches the union of its clauses.
 */
public class BLSpanOrQuery extends SpanOrQuery implements Cloneable {

	String field;

	/** Construct a SpanOrQuery merging the provided clauses.
	 * All clauses must have the same field.
	 * @param clauses clauses to OR together
	 */
	public BLSpanOrQuery(SpanQuery... clauses) {
		super(clauses);
		this.field = super.getField();
	}

	@Override
	public BLSpanOrQuery clone() {
		return BLSpanOrQuery.from(super.clone());
	}

	static BLSpanOrQuery from(SpanOrQuery in) {
		SpanQuery[] clauses = in.getClauses();
		SpanQuery[] blClauses = new SpanQuery[clauses.length];
		for (int i = 0; i < clauses.length; i++) {
			blClauses[i] = BLSpansWrapper.blSpanQueryFrom(clauses[i]);
		}
		BLSpanOrQuery out = new BLSpanOrQuery(blClauses);
		out.setBoost(in.getBoost());
		return out;
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
	public Query rewrite(IndexReader reader) throws IOException {
		return BLSpansWrapper.blSpanQueryFrom((SpanQuery)super.rewrite(reader));
	}

	@Override
	public String toString(String field) {
		return "BL" + super.toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof BLSpanOrQuery) {
			return super.equals(o);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ 0xB1ACC1AB;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		List<SpanWeight> subWeights = new ArrayList<>(getClauses().length);
		for (SpanQuery q: getClauses()) {
			subWeights.add(q.createWeight(searcher, false));
		}
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(subWeights) : null;
		return new SpanOrWeight(searcher, contexts, subWeights);
	}

	public class SpanOrWeight extends SpanWeight {
		final List<SpanWeight> subWeights;

		public SpanOrWeight(IndexSearcher searcher, Map<Term, TermContext> terms, List<SpanWeight> subWeights) throws IOException {
			super(BLSpanOrQuery.this, searcher, terms);
			this.subWeights = subWeights;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			for (final SpanWeight w: subWeights) {
				w.extractTerms(terms);
			}
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			for (SpanWeight w: subWeights) {
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
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {

			// BL: Create a clauseList and compute clauseLength and clausesAllSameLength
			// for the anonymous BLSpan class
			boolean clausesAllSameLengthSetter = true;
			int clauseLengthSetter = -1;

			final ArrayList<BLSpans> subSpans = new ArrayList<>(getClauses().length);

			for (SpanWeight w: subWeights) {
				BLSpans spans = BLSpansWrapper.optWrap(w.getSpans(context, requiredPostings));
				if (spans != null) {
					subSpans.add(spans);

					// BL: see if this clauses violates the same-length property
					if (spans.hitsAllSameLength()
							&& (clauseLengthSetter == -1 || clauseLengthSetter == spans
									.hitsLength())) {
						// This clause doesn't violate the all-same-length
						// requirements
						clauseLengthSetter = spans.hitsLength();
					} else {
						// This clause does violate the all-same-length requirements
						clausesAllSameLengthSetter = false;
					}
				}
			}

			if (subSpans.isEmpty()) {
				return null;
			} else if (subSpans.size() == 1) {
				return BLSpansWrapper.optWrap(subSpans.get(0));
			}

			final DisiPriorityQueue<Spans> byDocQueue = new DisiPriorityQueue<>(subSpans.size());
			for (Spans spans: subSpans) {
				byDocQueue.add(new DisiWrapper<>(spans));
			}

			final SpanPositionQueue byPositionQueue = new SpanPositionQueue(subSpans.size());
                                                                                // when empty use -1

			// BL
			final boolean clausesAllSameLength = clausesAllSameLengthSetter;
			final int clauseLength;
			if (clausesAllSameLength) {
				clauseLength = clauseLengthSetter;
			} else {
				clauseLength = -1;
			}

			return new BLSpans() {
				Spans topPositionSpans = null;

				@Override
				public int nextDoc() throws IOException {
					topPositionSpans = null;
					DisiWrapper<Spans> topDocSpans = byDocQueue.top();
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
					DisiWrapper<Spans> topDocSpans = byDocQueue.top();
					do {
						topDocSpans.doc = topDocSpans.iterator.advance(target);
						topDocSpans = byDocQueue.updateTop();
					} while (topDocSpans.doc < target);
					return topDocSpans.doc;
				}

				@Override
				public int docID() {
					DisiWrapper<Spans> topDocSpans = byDocQueue.top();
					return topDocSpans.doc;
				}

				@Override
				public TwoPhaseIterator asTwoPhaseIterator() {
					boolean hasApproximation = false;
					for (DisiWrapper<Spans> w: byDocQueue) {
						if (w.twoPhaseView != null) {
							hasApproximation = true;
							break;
						}
					}

					if (!hasApproximation) { // none of the sub spans supports approximations
						return null;
					}

					return new TwoPhaseIterator(new DisjunctionDISIApproximation<Spans>(byDocQueue)) {
						@Override
						public boolean matches() throws IOException {
							return twoPhaseCurrentDocMatches();
						}
					};
				}

				int lastDocTwoPhaseMatched = -1;

				boolean twoPhaseCurrentDocMatches() throws IOException {
					DisiWrapper<Spans> listAtCurrentDoc = byDocQueue.topList();
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
					DisiWrapper<Spans> listAtCurrentDoc = byDocQueue.topList();
					while (listAtCurrentDoc != null) {
						Spans spansAtDoc = listAtCurrentDoc.iterator;
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
					return "spanOr(" + BLSpanOrQuery.this + ")@" + docID() + ": " + startPosition() + " - " + endPosition();
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

				// BL: added guarantee-methods

				@Override
				public boolean hitsEndPointSorted() {
					return false; // cannot guarantee because we're merging from
									// different sources
				}

				@Override
				public boolean hitsStartPointSorted() {
					return true; // our way of merging guarantees this, as it should
									// for almost all BLSpans
				}

				@Override
				public boolean hitsAllSameLength() {
					return clausesAllSameLength;
				}

				@Override
				public int hitsLength() {
					return clauseLength;
				}

				@Override
				public boolean hitsHaveUniqueStart() {
					return false; // cannot guarantee because we're merging from
									// different sources
				}

				@Override
				public boolean hitsHaveUniqueEnd() {
					return false; // cannot guarantee because we're merging from
									// different sources
				}

				@Override
				public boolean hitsAreUnique() {
					return false; // cannot guarantee because we're merging from
									// different sources
				}

				@Override
				public void passHitQueryContextToClauses(HitQueryContext theContext) {
					for (BLSpans spans : subSpans) {
						spans.setHitQueryContext(theContext);
					}
				}

				@Override
				public void getCapturedGroups(Span[] capturedGroups) {
					if (!childClausesCaptureGroups)
						return;
					((BLSpans) topPositionSpans).getCapturedGroups(capturedGroups);
				}

				// NOTE 2016-07-01: the version below has a bug. Try searching for "marines" "had" "l.*" in the Brown corpus;
				//  you won't find anything, but searching for "marines" "had" "la.*" will. The version below will skip over
				//  some hits some of the time to cause this bug. For now, we're using the naive (slower) default implementation
				//  in BLSpans.
//				@Override
//				public int advanceStartPosition(int target) throws IOException {
//					// (JN: adapted from fillPositionQueue())
//
//					byPositionQueue.clear(); // start with empty position queue and re-add spans
//
//					// add all matching Spans at current doc to byPositionQueue
//					DisiWrapper<Spans> listAtCurrentDoc = byDocQueue.topList();
//					while (listAtCurrentDoc != null) {
//						Spans spansAtDoc = listAtCurrentDoc.iterator;
//						if (lastDocTwoPhaseMatched == listAtCurrentDoc.doc) { // matched
//																				// by
//																				// DisjunctionDisiApproximation
//							if (listAtCurrentDoc.twoPhaseView != null) { // matched
//																			// by
//																			// approximation
//								if (listAtCurrentDoc.lastApproxNonMatchDoc == listAtCurrentDoc.doc) { // matches()
//																										// returned
//																										// false
//									spansAtDoc = null;
//								} else {
//									if (listAtCurrentDoc.lastApproxMatchDoc != listAtCurrentDoc.doc) {
//										if (!listAtCurrentDoc.twoPhaseView
//												.matches()) {
//											spansAtDoc = null;
//										}
//									}
//								}
//							}
//						}
//
//						if (spansAtDoc != null) {
//							assert spansAtDoc.docID() == listAtCurrentDoc.doc;
//							//JN assert spansAtDoc.startPosition() == -1;
//							BLSpans.advanceStartPosition(spansAtDoc, target);
//							if (spansAtDoc.startPosition() != NO_MORE_POSITIONS) // JN WAS: assert spansAtDoc.startPosition() != NO_MORE_POSITIONS
//								byPositionQueue.add(spansAtDoc);
//						}
//						listAtCurrentDoc = listAtCurrentDoc.next;
//					}
//					if (byPositionQueue.size() == 0)
//						return NO_MORE_POSITIONS;
//
//					topPositionSpans = byPositionQueue.top();
//					return topPositionSpans.startPosition();
//				}
			};
		}
	}

}
