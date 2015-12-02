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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TextPatternPositionFilter;
import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;
import nl.inl.blacklab.search.TextPatternTranslator;
import nl.inl.blacklab.search.sequences.SpanQueryExpansion;
import nl.inl.blacklab.search.sequences.SpanQueryFilterNGrams;
import nl.inl.blacklab.search.sequences.SpanQueryRepetition;
import nl.inl.blacklab.search.sequences.SpanQuerySequence;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * Translates a TextPattern to a Lucene SpanQuery object.
 */
public class TextPatternTranslatorSpanQuery extends TextPatternTranslator<SpanQuery> {

	final static boolean USE_TAGS_WITH_PAYLOAD = true;

	@Override
	public SpanQuery and(QueryExecutionContext context, List<SpanQuery> clauses) {
		return new SpanQueryAnd(clauses);
	}

	@Override
	public SpanQuery andNot(QueryExecutionContext context, SpanQuery include, SpanQuery exclude) {
		return new SpanQueryPositionFilter(include, exclude, TextPatternPositionFilter.Operation.MATCHES, true);
	}

	@Override
	public SpanQuery or(QueryExecutionContext context, List<SpanQuery> clauses) {
		return new BLSpanOrQuery(clauses.toArray(new SpanQuery[] {}));
	}

	@Override
	public SpanQuery regex(QueryExecutionContext context, String value) {
		String valueNoStartEndMatch = value.replaceAll("\\^|\\$", "");
		return new BLSpanMultiTermQueryWrapper<RegexpQuery>(new RegexpQuery(
				new Term(context.luceneField(), context.optDesensitize(valueNoStartEndMatch))));
	}

	@Override
	public SpanQuery sequence(QueryExecutionContext context, List<SpanQuery> clauses) {
		return new SpanQuerySequence(clauses);
	}

	@Override
	public SpanQuery docLevelAnd(QueryExecutionContext context, List<SpanQuery> clauses) {
		return new SpanQueryDocLevelAnd(clauses);
	}

	@Override
	public SpanQuery fuzzy(QueryExecutionContext context, String value, int maxEdits, int prefixLength) {
		return new SpanFuzzyQuery(new Term(context.luceneField(), context.optDesensitize(value)), maxEdits, prefixLength);
	}

	@Override
	public SpanQuery tags(QueryExecutionContext context, String elementName, Map<String, String> attr) {
		SpanQuery allTags;
		if (USE_TAGS_WITH_PAYLOAD)
			allTags = new SpanQueryTagsPayload(context, elementName);
		else
			allTags = new SpanQueryTags(context, elementName);
		if (attr == null || attr.size() == 0)
			return allTags;

		// Construct attribute filters
		List<SpanQuery> attrFilters = new ArrayList<SpanQuery>();
		QueryExecutionContext startTagContext = context.withProperty(ComplexFieldUtil.START_TAG_PROP_NAME);
		for (Map.Entry<String,String> e: attr.entrySet()) {
			String value = optInsensitive(context, "@" + e.getKey() + "__" + e.getValue());
			attrFilters.add(term(startTagContext, value));
		}

		// Filter the tags
		// (NOTE: only works for start tags and full elements because attribute values
		//  are indexed at the start tag!)
		SpanQuery filter = new SpanQueryAnd(attrFilters);
		return new SpanQueryPositionFilter(allTags, filter, TextPatternPositionFilter.Operation.STARTS_AT, false);
	}

//	@Override
//	public SpanQuery containing(QueryExecutionContext context, SpanQuery containers, SpanQuery search, boolean invert) {
//		return new SpanQueryPositionFilter(containers, search, SpanQueryPositionFilter.Filter.CONTAINING, invert);
//	}
//
//	@Override
//	public SpanQuery within(QueryExecutionContext context, SpanQuery search, SpanQuery containers, boolean invert) {
//		return new SpanQueryPositionFilter(search, containers, SpanQueryPositionFilter.Filter.WITHIN, invert);
//	}

	@Override
	public SpanQuery positionFilter(QueryExecutionContext context,
			SpanQuery producer, SpanQuery filter, Operation op, boolean invert,
			int leftAdjust, int rightAdjust) {
		return new SpanQueryPositionFilter(producer, filter, op, invert, leftAdjust, rightAdjust);
	}

	@Override
	public SpanQuery startsAt(QueryExecutionContext context, SpanQuery producer, SpanQuery filter) {
		return new SpanQueryPositionFilter(producer, filter, TextPatternPositionFilter.Operation.STARTS_AT, false);
	}

	@Override
	public SpanQuery endsAt(QueryExecutionContext context, SpanQuery producer, SpanQuery filter) {
		return new SpanQueryPositionFilter(producer, filter, TextPatternPositionFilter.Operation.ENDS_AT, false);
	}

	@Override
	public SpanQuery term(QueryExecutionContext context, String value) {
		// Use a BlackLabSpanTermQuery instead of default Lucene one
		// because we need to override getField() to only return the base field name,
		// not the complete field name with the property.
		return new BLSpanTermQuery(new Term(context.luceneField(), context.optDesensitize(value)));
	}

	@Override
	public SpanQuery expand(QueryExecutionContext context, SpanQuery clause, boolean expandToLeft, int min, int max) {
		SpanQueryExpansion spanQueryExpansion = new SpanQueryExpansion(clause, expandToLeft, min, max);
		spanQueryExpansion.setIgnoreLastToken(context.alwaysHasClosingToken());
		return spanQueryExpansion;
	}

	@Override
	public SpanQuery filterNGrams(QueryExecutionContext context, SpanQuery clause, Operation op, int min, int max) {
		return new SpanQueryFilterNGrams(clause, op, min, max);
	}

	@Override
	public SpanQuery repetition(SpanQuery clause, int min, int max) {
		return new SpanQueryRepetition(clause, min, max);
	}

	@Override
	public SpanQuery docLevelAndNot(SpanQuery include, SpanQuery exclude) {
		return new SpanQueryDocLevelAndNot(include, exclude);
	}

	@Override
	public SpanQuery wildcard(QueryExecutionContext context, String value) {
		return new BLSpanMultiTermQueryWrapper<WildcardQuery>(new WildcardQuery(new Term(context.luceneField(),
				context.optDesensitize(value))));
	}

	@Override
	public SpanQuery prefix(QueryExecutionContext context, String value) {
		return new BLSpanMultiTermQueryWrapper<PrefixQuery>(new PrefixQuery(new Term(context.luceneField(),
				context.optDesensitize(value))));
	}

	@Override
	public SpanQuery not(QueryExecutionContext context, SpanQuery clause) {
		SpanQueryNot spanQueryNot = new SpanQueryNot(clause);
		spanQueryNot.setIgnoreLastToken(context.alwaysHasClosingToken());
		return spanQueryNot;
	}

	@Override
	public SpanQuery any(QueryExecutionContext context, int min, int max) {
		return new SpanQueryNGrams(context.alwaysHasClosingToken(), context.luceneField(), min, max);
		//return SpanQueryNot.matchAllTokens(context.alwaysHasClosingToken(), context.luceneField());
	}

	@Override
	public SpanQuery edge(SpanQuery clause, boolean rightEdge) {
		return new SpanQueryEdge(clause, rightEdge);
	}

	@Override
	public SpanQuery captureGroup(SpanQuery clause, String name) {
		return new SpanQueryCaptureGroup(clause, name);
	}

}
