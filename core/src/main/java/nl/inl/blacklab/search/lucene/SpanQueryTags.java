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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.util.StringUtil;

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

	private Map<String, String> attr;

	private String startTagFieldName;

	public SpanQueryTags(String startTagFieldName, String tagName, Map<String, String> attr) {
		this.tagName = tagName;
		baseFieldName = ComplexFieldUtil.getBaseName(startTagFieldName);
		this.startTagFieldName = startTagFieldName;
		this.clause = new BLSpanTermQuery(new Term(startTagFieldName, tagName));
		this.attr = attr != null && attr.isEmpty() ? null : attr;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		if (attr == null)
			return this;

		// Construct attribute filters
		List<BLSpanQuery> attrFilters = new ArrayList<>();
		for (Map.Entry<String,String> e: attr.entrySet()) {
			String value = "@" + e.getKey() + "__" + e.getValue();
			attrFilters.add((BLSpanQuery) new BLSpanTermQuery(new Term(startTagFieldName, value)));
		}

		// Filter the tags
		// (NOTE: only works for start tags and full elements because attribute values
		//  are indexed at the start tag!)
		BLSpanQuery filter;
		if (attrFilters.size() == 1)
			filter = attrFilters.get(0);
		else
			filter = new SpanQueryAnd(attrFilters);
		return new SpanQueryPositionFilter(new SpanQueryTags(startTagFieldName, tagName, null), filter, SpanQueryPositionFilter.Operation.STARTS_AT, false);
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		if (attr != null)
			throw new RuntimeException("Query should've been rewritten! (attr != null)");
		BLSpanWeight weight = clause.createWeight(searcher, needsScores);
		return new SpanWeightTags(weight, searcher, needsScores ? getTermContexts(weight) : null);
	}

	public class SpanWeightTags extends BLSpanWeight {

		final BLSpanWeight weight;

		public SpanWeightTags(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
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
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans startTags = weight.getSpans(context, requiredPostings);
			if (startTags == null)
				return null;
			return new SpansTags(startTags);
		}
	}

	@Override
	public String toString(String field) {
		if (attr != null && !attr.isEmpty())
			return "TAGS(" + tagName + ", " + StringUtil.join(attr) + ")";
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
		if (attr == null) {
			if (that.attr != null) {
				return false;
			}
		} else if (!attr.equals(that.attr)) {
			return false;
		}

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
	public String getRealField() {
		return startTagFieldName;
	}

	@Override
	public int hashCode() {
		int h = clause.hashCode();
		if (attr != null)
			h ^= attr.hashCode();
		h ^= (h << 10) | (h >>> 23);
		return h;
	}

	public String getElementName() {
		return tagName;
	}

	@Override
	public boolean hitsAllSameLength() {
		return false;
	}

	@Override
	public int hitsLengthMin() {
		return 0;
	}

	@Override
	public int hitsLengthMax() {
		return Integer.MAX_VALUE;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return false;
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return false;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return false;
	}

	@Override
	public boolean hitsAreUnique() {
		return false;
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		return clause.estimatedNumberOfHits(reader);
	}
}
