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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.DisiPriorityQueue;
import org.apache.lucene.search.DisiWrapper;
import org.apache.lucene.search.DisjunctionDISIApproximation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.PriorityQueue;

import nl.inl.blacklab.search.Span;

/**
 * Matches the union of its clauses.
 *
 * BL-specific version (search for "// BL") that produces a BLSpans.
 */
@SuppressWarnings({ "javadoc" })
// BL: suppress some innocent warnings
public class BLSpanOrQuery extends SpanOrQuery {

	private String field;

	/** Construct a BLSpanOrQuery merging the provided clauses. */
	public BLSpanOrQuery(SpanQuery... clauses) {
		super(clauses);
		field = getField();
	}

	@Override
	public String getField() {
		return field; // may be overridden in setField in case of 0-clause or
						// query
		// (I believe this was a workaround to an old Lucene bug which may not
		// be needed anymore..)
	}

	@Override
	public void extractTerms(Set<Term> terms) {
		try {
			// FIXME: temporary extractTerms hack
			Method methodExtractTerms = SpanQuery.class.
			        getDeclaredMethod("extractTerms", Set.class);
			methodExtractTerms.setAccessible(true);

			for (final SpanQuery clause : getClauses()) {
			    methodExtractTerms.invoke(clause, terms);
				//clause.extractTerms(terms);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public BLSpanOrQuery clone() {
		SpanQuery[] clauses = getClauses();
		SpanQuery[] newClauses = new SpanQuery[clauses.length];
		for (int i = 0; i < clauses.length; i++) {
			newClauses[i] = (SpanQuery) clauses[i].clone();
		}
		BLSpanOrQuery soq = new BLSpanOrQuery(newClauses);
		soq.setBoost(getBoost());
		return soq;
	}

	@Override
	public Query rewrite(IndexReader reader) throws IOException {
		SpanQuery[] clauses = getClauses();
		SpanQuery[] cloneClauses = null;
		for (int i = 0; i < clauses.length; i++) {
			SpanQuery c = clauses[i];
			SpanQuery query = (SpanQuery) c.rewrite(reader);
			if (query != c) { // clause rewrote: must clone
				if (cloneClauses == null) {
					cloneClauses = new SpanQuery[clauses.length];
					for (int j = 0; j < i; j++) {
						cloneClauses[j] = clauses[j];
					}
				}
				cloneClauses[i] = query;
			} else {
				if (cloneClauses != null)
					cloneClauses[i] = c; // original clause
			}
		}
		if (cloneClauses != null) {
			BLSpanOrQuery clone = null;
			clone = new BLSpanOrQuery(cloneClauses);
			clone.setBoost(getBoost());
			if (clone.getField() == null)
				clone.setField(field);
			return clone; // some clauses rewrote
		}
		return this; // no clauses rewrote
	}

	@Override
	public String toString(String field) {
		return "BL" + super.toString(field);
	}

	@Override
	public boolean equals(Object o) {
		return super.equals(o) && field.equals(((BLSpanOrQuery) o).field);
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ field.hashCode();
	}

	@Override
	public Spans getSpans(final LeafReaderContext context,
			final Bits acceptDocs, final Map<Term, TermContext> termContexts)
			throws IOException {
		SpanQuery[] clauses = getClauses();
		// Create a clauseList and compute clauseLength and clausesAllSameLength
		// for the anonymous BLSpan class
		boolean clausesAllSameLengthSetter = true;
		int clauseLengthSetter = -1;
		final ArrayList<BLSpans> subSpans = new ArrayList<BLSpans>(
				clauses.length);
		for (SpanQuery sq : clauses) {
			BLSpans spans = BLSpansWrapper.optWrap(sq.getSpans(context,
					acceptDocs, termContexts));
			if (spans != null) {
				subSpans.add(spans);
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
		if (subSpans.size() == 0)
			return null;
		else if (subSpans.size() == 1)
			return BLSpansWrapper.optWrap(subSpans.get(0));

		final DisiPriorityQueue<Spans> byDocQueue = new DisiPriorityQueue<>(
				subSpans.size());
		for (Spans spans : subSpans) {
			byDocQueue.add(new DisiWrapper<>(spans));
		}

		/* BL: Class copied from Lucene because package-private */
		class SpanPositionQueue extends PriorityQueue<Spans> {
			SpanPositionQueue(int maxSize) {
				super(maxSize, false); // do not prepopulate
			}

			@Override
			protected boolean lessThan(Spans s1, Spans s2) {
				int start1 = s1.startPosition();
				int start2 = s2.startPosition();
				return (start1 < start2) ? true : (start1 == start2) ? s1
						.endPosition() < s2.endPosition() : false;
			}
		}

		final SpanPositionQueue byPositionQueue = new SpanPositionQueue(
				subSpans.size()); // when empty use -1

		final boolean clausesAllSameLength = clausesAllSameLengthSetter;
		final int clauseLength;
		if (clausesAllSameLength) {
			clauseLength = clauseLengthSetter;
		} else {
			clauseLength = -1;
		}

		return new BLSpans() { // BL: was Spans

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
				for (DisiWrapper<Spans> w : byDocQueue) {
					if (w.twoPhaseView != null) {
						hasApproximation = true;
						break;
					}
				}

				if (!hasApproximation) { // none of the sub spans supports
											// approximations
					return null;
				}

				return new TwoPhaseIterator(
						new DisjunctionDISIApproximation<Spans>(byDocQueue)) {
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

			void fillPositionQueue() throws IOException { // called at first
															// nextStartPosition
				assert byPositionQueue.size() == 0;
				// add all matching Spans at current doc to byPositionQueue
				DisiWrapper<Spans> listAtCurrentDoc = byDocQueue.topList();
				while (listAtCurrentDoc != null) {
					Spans spansAtDoc = listAtCurrentDoc.iterator;
					if (lastDocTwoPhaseMatched == listAtCurrentDoc.doc) { // matched
																			// by
																			// DisjunctionDisiApproximation
						if (listAtCurrentDoc.twoPhaseView != null) { // matched
																		// by
																		// approximation
							if (listAtCurrentDoc.lastApproxNonMatchDoc == listAtCurrentDoc.doc) { // matches()
																									// returned
																									// false
								spansAtDoc = null;
							} else {
								if (listAtCurrentDoc.lastApproxMatchDoc != listAtCurrentDoc.doc) {
									if (!listAtCurrentDoc.twoPhaseView
											.matches()) {
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
					fillPositionQueue(); // fills byPositionQueue at first
											// position
					topPositionSpans = byPositionQueue.top();
				} else {
					topPositionSpans.nextStartPosition();
					topPositionSpans = byPositionQueue.updateTop();
				}
				return topPositionSpans.startPosition();
			}

			@Override
			public int startPosition() {
				return topPositionSpans == null ? -1 : topPositionSpans
						.startPosition();
			}

			@Override
			public int endPosition() {
				return topPositionSpans == null ? -1 : topPositionSpans
						.endPosition();
			}

			@Override
			public Collection<byte[]> getPayload() throws IOException {
				return topPositionSpans == null ? null : topPositionSpans
						.isPayloadAvailable() ? new ArrayList<>(
						topPositionSpans.getPayload()) : null;
			}

			@Override
			public boolean isPayloadAvailable() throws IOException {
				return (topPositionSpans != null)
						&& topPositionSpans.isPayloadAvailable();
			}

			@Override
			public String toString() {
				return "BLspanOr(" + BLSpanOrQuery.this + ")@" + docID() + ": "
						+ startPosition() + " - " + endPosition();
			}

			long cost = -1;

			@Override
			public long cost() {
				if (cost == -1) {
					cost = 0;
					for (Spans spans : subSpans) {
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
			public void passHitQueryContextToClauses(HitQueryContext context) {
				for (BLSpans spans : subSpans) {
					spans.setHitQueryContext(context);
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

	/**
	 * Convert a SpanOrQuery to a BLSpanOrQuery
	 *
	 * @param soq
	 * @return
	 */
	public static BLSpanOrQuery from(SpanOrQuery soq) {
		BLSpanOrQuery blsoq = new BLSpanOrQuery(soq.getClauses());
		blsoq.setBoost(soq.getBoost());
		if (blsoq.getField() == null)
			blsoq.setField(soq.getField()); // rewritten to or query without
											// clauses
		return blsoq;
	}

}
