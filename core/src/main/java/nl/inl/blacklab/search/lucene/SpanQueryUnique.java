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
 * Makes sure the resulting hits do not contain consecutive duplicate hits. These may arise when
 * e.g. combining multiple SpanFuzzyQueries with OR.
 */
public class SpanQueryUnique extends BLSpanQuery {
	private BLSpanQuery src;

	public SpanQueryUnique(BLSpanQuery src) {
		this.src = src;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		BLSpanQuery rewritten = src.rewrite(reader);
		if (rewritten != src) {
			return new SpanQueryUnique(rewritten);
		}
		return this;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight weight = src.createWeight(searcher, needsScores);
		return new SpanWeightUnique(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightUnique extends SpanWeight {

		final SpanWeight weight;

		public SpanWeightUnique(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryUnique.this, searcher, terms);
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
			Spans srcSpans = weight.getSpans(context, requiredPostings);
			if (srcSpans == null)
				return null;
			return new SpansUnique(srcSpans);
		}
	}

	@Override
	public String toString(String field) {
		return "SpanQueryUnique(" + src + ")";
	}

	@Override
	public String getField() {
		return src.getField();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o instanceof SpanQueryUnique) {
			final SpanQueryUnique that = (SpanQueryUnique) o;
			return src.equals(that.src);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return src.hashCode() ^ 0x98764038;
	}
}
