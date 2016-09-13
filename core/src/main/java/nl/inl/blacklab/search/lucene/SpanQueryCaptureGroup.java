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
 * Captures its clause as a captured group.
 */
public class SpanQueryCaptureGroup extends BLSpanQueryAbstract {

	private String name;

	/**
	 * Construct SpanQueryCaptureGroup object.
	 * @param query the query to determine edges from
	 * @param name captured group name
	 */
	public SpanQueryCaptureGroup(BLSpanQuery query, String name) {
		super(query);
		this.name = name;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		List<BLSpanQuery> rewritten = rewriteClauses(reader);
		return rewritten == null ? this : new SpanQueryCaptureGroup(rewritten.get(0), name);
	}

	@Override
	public boolean matchesEmptySequence() {
		return clauses.get(0).matchesEmptySequence();
	}

	@Override
	public BLSpanQuery noEmpty() {
		if (!matchesEmptySequence())
			return this;
		return new SpanQueryCaptureGroup(clauses.get(0).noEmpty(), name);
	}

	@Override
	public boolean hasConstantLength() {
		return clauses.get(0).hasConstantLength();
	}

	@Override
	public int getMinLength() {
		return clauses.get(0).getMinLength();
	}

	@Override
	public int getMaxLength() {
		return clauses.get(0).getMaxLength();
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight weight = clauses.get(0).createWeight(searcher, needsScores);
		return new SpanWeightCaptureGroup(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightCaptureGroup extends SpanWeight {

		final SpanWeight weight;

		public SpanWeightCaptureGroup(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryCaptureGroup.this, searcher, terms);
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
			return new SpansCaptureGroup(spans, name);
		}

	}

	@Override
	public String toString(String field) {
		return "CAPTURE(" + clausesToString(field) + ", " + name + ")";
	}
}
