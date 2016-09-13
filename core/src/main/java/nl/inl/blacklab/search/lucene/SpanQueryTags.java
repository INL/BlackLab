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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.QueryExecutionContext;

/**
 *
 * Returns spans corresponding to a certain element (tag) type.
 *
 * For example, SpanQueryTags("ne") will give us spans for all the <ne> elements in the document.
 */
public class SpanQueryTags extends BLSpanQuery {

	BLSpanTermQuery clause;

	private String tagName;

	private String baseFieldName;

	public SpanQueryTags(QueryExecutionContext context, String tagName) {
		this.tagName = tagName;
		baseFieldName = context.fieldName();
		QueryExecutionContext startTagContext = context.withProperty(ComplexFieldUtil.START_TAG_PROP_NAME);
		String startTagFieldName = startTagContext.luceneField();
		this.clause = new BLSpanTermQuery(new Term(startTagFieldName, startTagContext.optDesensitize(tagName)));
	}

	@Override
	public boolean hasConstantLength() {
		return false;
	}

	@Override
	public int getMinLength() {
		return 0;
	}

	@Override
	public int getMaxLength() {
		return Integer.MAX_VALUE;
	}

	@Override
	public SpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		SpanWeight weight = clause.createWeight(searcher, needsScores);
		return new SpanWeightTags(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightTags extends SpanWeight {

		final SpanWeight weight;

		public SpanWeightTags(SpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryTags.this, searcher, terms);
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
			Spans startTags = weight.getSpans(context, requiredPostings);
			if (startTags == null)
				return null;
			return new SpansTags(startTags);
		}
	}

	@Override
	public String toString(String field) {
		return "TAGS(" + tagName + ")";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || this.getClass() != o.getClass())
			return false;

		final SpanQueryTags that = (SpanQueryTags) o;

		if (!clause.equals(that.clause))
			return false;

		return true;
	}

	/**
	 * Returns the name of the search field. In the case of a complex field, the clauses may
	 * actually query different properties of the same complex field (e.g. "description" and
	 * "description__pos"). That's why only the prefix is returned.
	 *
	 * @return name of the search field. In the case of a complex
	 */
	@Override
	public String getField() {
		return baseFieldName;
	}

	@Override
	public int hashCode() {
		int h = clause.hashCode();
		h ^= (h << 10) | (h >>> 23);
		return h;
	}

	public String getElementName() {
		return tagName;
	}
}
