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
public class SpanQueryRepetition extends SpanQueryBase {
	private int min;

	private int max;

	public SpanQueryRepetition(BLSpanQuery clause, int min, int max) {
		super(clause);
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new IllegalArgumentException("min > max");
		if (min < 1)
			throw new IllegalArgumentException("min < 1");
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
		BLSpanQuery[] rewritten = rewriteClauses(reader);
		return rewritten == null ? this : new SpanQueryRepetition(rewritten[0], min, max);
	}

	/**
	 * Repetition query matches the empty sequence iff min == 0 or its
	 * base clause matches the empty sequence.
	 */
	@Override
	public boolean matchesEmptySequence() {
		return clauses[0].matchesEmptySequence() || min == 0;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight weight = clauses[0].createWeight(searcher, needsScores);
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
		return "SpanQueryRepetition(" + clauses[0] + ", " + min + ", " + max + ")";
	}
}
