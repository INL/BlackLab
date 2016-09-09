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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * Combines spans, keeping only combinations of hits that occur one after the other. The order is
 * significant: a hit from the first span must be followed by a hit from the second.
 *
 * Note that this class is different from org.apache.lucene.search.spans.SpanNearQuery: it tries to
 * make sure it generates *all* possible sequence matches. SpanNearQuery doesn't do this; once a hit
 * is used in a SpanNearQuery match, it advances to the next hit.
 *
 * In the future, this class could be expanded to make the exact behaviour configurable: find all
 * matches / find longest matches / find shortest matches / ...
 *
 * See SpanSequenceRaw for details on the matching process.
 */
public class SpanQuerySequence extends SpanQueryBase {
	public SpanQuerySequence(BLSpanQuery first, BLSpanQuery second) {
		super(first, second);
	}

	public SpanQuerySequence(Collection<BLSpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQuerySequence(BLSpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery[] rewritten = rewriteClauses(reader);
		return rewritten == null ? this : new SpanQuerySequence(rewritten);
	}

	@Override
	public boolean matchesEmptySequence() {
		for (BLSpanQuery cl: clauses) {
			if (!cl.matchesEmptySequence())
				return false;
		}
		return true;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		List<SpanWeight> weights = new ArrayList<>();
		for (BLSpanQuery clause: clauses) {
			weights.add(clause.createWeight(searcher, needsScores));
		}
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(weights.toArray(new SpanWeight[0])) : null;
		return new SpanWeightSequence(weights, searcher, contexts);
	}

	public class SpanWeightSequence extends SpanWeight {

		final List<SpanWeight> weights;

		public SpanWeightSequence(List<SpanWeight> weights, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQuerySequence.this, searcher, terms);
			this.weights = weights;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			for (SpanWeight weight: weights) {
				weight.extractTerms(terms);
			}
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			for (SpanWeight weight: weights) {
				weight.extractTermContexts(contexts);
			}
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans combi = BLSpansWrapper.optWrap(weights.get(0).getSpans(context, requiredPostings));
			if (combi == null)
				return null;
			for (int i = 1; i < weights.size(); i++) {
				BLSpans si = BLSpansWrapper.optWrap(weights.get(i).getSpans(context, requiredPostings));
				if (si == null)
					return null;

				// Note: the spans coming from SequenceSpansRaw are not sorted by end point.
				// This is okay in this loop because combi is used as the left part of the next
				// sequence (so it is explicitly sorted by end point when we put it back in
				// SequenceSpansRaw for the next part of the sequence), but before returning the
				// final spans, we wrap it in a per-document (start-point) sorter.
				if (si.hitsStartPointSorted() && si.hitsHaveUniqueStart() &&
						combi.hitsEndPointSorted() && combi.hitsHaveUniqueEnd()) {
					// We can take a shortcut because of what we know about the Spans we're combining.
					combi = new SpansSequenceSimple(combi, si);
				}
				else {
					combi = new SpansSequenceRaw(combi, si);
				}
			}

			// Sort the resulting spans by start point.
			// Note that duplicates may have formed by combining spans from left and right. Eliminate
			// these duplicates now (hence the 'true').
			boolean sorted = combi.hitsStartPointSorted();
			boolean unique = combi.hitsAreUnique();
			if (!sorted) {
				combi = new PerDocumentSortedSpans(combi, false, !unique);
			} else if (!unique) {
				combi = new SpansUnique(combi);
			}
			return combi;
		}

	}

	@Override
	public String toString(String field) {
		return "SpanQuerySequence(" + clausesToString(field) + ")";
	}
}
