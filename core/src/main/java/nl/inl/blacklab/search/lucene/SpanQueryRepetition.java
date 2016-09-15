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
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

/**
 * Finds repeated consecutive hits.
 *
 * This generates all possible sequences of consecutive hits, so if we search
 * for B+ in the input string ABBBA, we'll get 3 hits of length 1, 2 hits of length 2,
 * and 1 hit of length 3. In the future, this should be made configurable (to specifically
 * support greedy/reluctant matching, etc.)
 */
public class SpanQueryRepetition extends BLSpanQueryAbstract {
	private int min;

	private int max;

	public SpanQueryRepetition(BLSpanQuery clause, int min, int max) {
		super(clause);
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new IllegalArgumentException("min > max");
		if (min < 0)
			throw new IllegalArgumentException("min < 0");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!super.equals(o))
			return false;

		final SpanQueryRepetition that = (SpanQueryRepetition) o;
		return min == that.min && max == that.max;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery base = clauses.get(0);
		BLSpanQuery baseRewritten = base.rewrite(reader);
		if (min == 1 && max == 1)
			return baseRewritten;
		if (baseRewritten instanceof SpanQueryAnyToken) {
			// Repeating anytoken clause can sometimes be expressed as simple anytoken clause
			SpanQueryAnyToken tp = (SpanQueryAnyToken)baseRewritten;
			if (tp.min == 1 && tp.max == 1) {
				// Repeat of a single any token
				return new SpanQueryAnyToken(min, max, base.getField());
			} else if (min == max && tp.min == tp.max) {
				// Exact number of any tokens
				int n = min * tp.min;
				return new SpanQueryAnyToken(n, n, base.getField());
			}
		} else if (baseRewritten.isSingleTokenNot() && min > 0) {
			// Rewrite to anytokens-not-containing form so we can optimize it
			// (note the check for min > 0 above, because position filter cannot match the empty sequence)
			int l = baseRewritten.getMinLength();
			BLSpanQuery container = new SpanQueryRepetition(new SpanQueryAnyToken(l, l, base.getField()), min, max);
			container = container.rewrite(reader);
			return new SpanQueryPositionFilter(container, baseRewritten.inverted(), SpanQueryPositionFilter.Operation.CONTAINING, true);
		} else if (baseRewritten instanceof SpanQueryRepetition) {
			SpanQueryRepetition tp = (SpanQueryRepetition)baseRewritten;
			if (max == -1 && tp.max == -1) {
				if (min >= 0 && min <= 1 && tp.min >= 0 && tp.min <= 1) {
					// A++, A+*, A*+, A**. Rewrite to single repetition.
					return new SpanQueryRepetition(tp.clauses.get(0), min * tp.min, max);
				}
			} else {
				if (min == 0 && max == 1 && tp.min == 0 && tp.max == 1) {
					// A?? == A?
					return tp;
				}
				if (min == 1 && max == 1) {
					// A{x,y}{1,1} == A{x,y}
					return new SpanQueryRepetition(tp.clauses.get(0), tp.min, tp.max);
				}
				// (other cases like A{1,1}{x,y} should have been rewritten already)
			}
		}
		if (baseRewritten == base)
			return this;
		return new SpanQueryRepetition(baseRewritten, min, max);
	}

	/**
	 * Repetition query matches the empty sequence iff min == 0 or its
	 * base clause matches the empty sequence.
	 */
	@Override
	public boolean matchesEmptySequence() {
		return clauses.get(0).matchesEmptySequence() || min == 0;
	}

	@Override
	public BLSpanQuery noEmpty() {
		if (!matchesEmptySequence())
			return this;
		int newMin = min == 0 ? 1 : min;
		return new SpanQueryRepetition(clauses.get(0).noEmpty(), newMin, max);
	}

	@Override
	public boolean hasConstantLength() {
		return clauses.get(0).hasConstantLength() && min == max;
	}

	@Override
	public int getMinLength() {
		return clauses.get(0).getMinLength() * min;
	}

	@Override
	public int getMaxLength() {
		return max < 0 ? Integer.MAX_VALUE : clauses.get(0).getMaxLength() * max;
	}

	@Override
	public BLSpanQuery combineWithPrecedingPart(BLSpanQuery previousPart, IndexReader reader) throws IOException {
		if (previousPart instanceof SpanQueryRepetition) {
			// Repetition clause.
			SpanQueryRepetition rep = (SpanQueryRepetition) previousPart;
			BLSpanQuery prevCl = rep.getClause();
			if (prevCl.equals(clauses.get(0))) {
				// Same clause; combine repetitions
				return new SpanQueryRepetition(clauses.get(0), min + rep.getMinRep(), addRepetitionMaxValues(rep.getMaxRep(), max));
			}
		} else {
			if (previousPart.equals(clauses.get(0))) {
				// Same clause; add one to min and max
				return new SpanQueryRepetition(clauses.get(0), min + 1, addRepetitionMaxValues(max, 1));
			}
		}
		return super.combineWithPrecedingPart(previousPart, reader);
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		if (min < 1)
			throw new RuntimeException("Query should have been rewritten! (min < 1)");
		SpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
		return new SpanWeightRepetition(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightRepetition extends SpanWeight {

		final SpanWeight weight;

		public SpanWeightRepetition(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryRepetition.this, searcher, terms);
			this.weight = weight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			weight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			weight.extractTermContexts(contexts);
		}

		@Override
		public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			Spans spans = weight.getSpans(context, requiredPostings);
			if (spans == null)
				return null;
			return new SpansRepetition(spans, min, max);
		}

	}

	@Override
	public int hashCode() {
		int h = clauses.hashCode();
		h ^= (h << 10) | (h >>> 23);
		h ^= min << 10;
		h ^= max << 5;
		return h;
	}

	@Override
	public String toString(String field) {
		return "REP(" + clauses.get(0) + ", " + min + ", " + max + ")";
	}

	public BLSpanQuery getClause() {
		return clauses.get(0);
	}

	public int getMinRep() {
		return min;
	}

	public int getMaxRep() {
		return max;
	}

}
