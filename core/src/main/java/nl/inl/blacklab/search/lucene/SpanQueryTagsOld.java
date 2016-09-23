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
import java.util.Arrays;
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
 * Old version of SpanQueryTags.
 *
 * Start and end tags used to be stored in separate properties.
 * This class is used for indices with indexFormat <= 3.
 * Starting from indexFormat 3.1, tag lengths are stored in the start tag payload.
 *
 *
 * Returns spans corresponding to a certain element (tag) type.
 *
 * For example, SpanQueryTags("ne") will give us spans for all the <ne> elements in the document.
 *
 * NOTE: this does not work with nested tags yet, as it just searches for all open and close tags
 * and matches the first open tag with the first close tag, etc.
 *
 */
public class SpanQueryTagsOld extends BLSpanQueryAbstract {

	private String tagName;

	private Map<String, String> attr;

	private String startTagFieldName;

	private String endTagFieldName;

	public SpanQueryTagsOld(String startTagFieldName, String endTagFieldName, String tagName, Map<String, String> attr) {
		super();
		this.tagName = tagName;
		baseFieldName = ComplexFieldUtil.getBaseName(startTagFieldName);
		this.startTagFieldName = startTagFieldName;
		this.endTagFieldName = endTagFieldName;

		// Use a BlackLabSpanTermQuery instead of default Lucene one
		// because we need to override getField() to only return the base field name,
		// not the complete field name with the property.
		clauses = new ArrayList<BLSpanQuery>(Arrays.asList(
			new BLSpanTermQuery(new Term(startTagFieldName, tagName)),
			new BLSpanTermQuery(new Term(endTagFieldName, tagName))
		));
		this.attr = attr.isEmpty() ? null : attr;
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
		return new SpanQueryPositionFilter(new SpanQueryTagsOld(startTagFieldName, endTagFieldName, tagName, null), filter, SpanQueryPositionFilter.Operation.STARTS_AT, false);
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
		if (attr != null)
			throw new RuntimeException("Query should've been rewritten! (attr != null)");
		BLSpanWeight startWeight = clauses.get(0).createWeight(searcher, needsScores);
		BLSpanWeight endWeight = clauses.get(1).createWeight(searcher, needsScores);
		Map<Term, TermContext> contexts = needsScores ? getTermContexts(startWeight, endWeight) : null;
		return new SpanWeightTagsOld(startWeight, endWeight, searcher, contexts);
	}

	public class SpanWeightTagsOld extends BLSpanWeight {

		final BLSpanWeight startWeight, endWeight;

		public SpanWeightTagsOld(BLSpanWeight startWeight, BLSpanWeight endWeight, IndexSearcher searcher, Map<Term, TermContext> terms) throws IOException {
			super(SpanQueryTagsOld.this, searcher, terms);
			this.startWeight = startWeight;
			this.endWeight = endWeight;
		}

		@Override
		public void extractTerms(Set<Term> terms) {
			startWeight.extractTerms(terms);
			endWeight.extractTerms(terms);
		}

		@Override
		public void extractTermContexts(Map<Term, TermContext> contexts) {
			startWeight.extractTermContexts(contexts);
			endWeight.extractTermContexts(contexts);
		}

		@Override
		public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
			BLSpans startTags = startWeight.getSpans(context, requiredPostings);
			BLSpans endTags = endWeight.getSpans(context, requiredPostings);
			if (startTags == null || endTags == null)
				return null;
			return new SpansTagsOld(startTags, endTags);
		}
	}

	@Override
	public String toString(String field) {
		if (attr != null && !attr.isEmpty())
			return "TAGS(" + tagName + ", " + StringUtil.join(attr) + ")";
		return "TAGS(" + tagName + ")";
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
		return clauses.get(0).estimatedNumberOfHits(reader);
	}
}
