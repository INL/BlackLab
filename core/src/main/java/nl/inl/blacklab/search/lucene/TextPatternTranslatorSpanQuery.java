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

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.WildcardQuery;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.RegexpTooLargeException;
import nl.inl.blacklab.search.TextPatternPositionFilter;
import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;
import nl.inl.blacklab.search.TextPatternTranslator;

/**
 * Translates a TextPattern to a Lucene BLSpanQuery object.
 */
public class TextPatternTranslatorSpanQuery extends TextPatternTranslator<BLSpanQuery> {

	@Override
	public BLSpanQuery and(QueryExecutionContext context, List<BLSpanQuery> clauses) {
		return new SpanQueryAnd(clauses);
	}

	@Override
	public BLSpanQuery andNot(QueryExecutionContext context, BLSpanQuery include, BLSpanQuery exclude) {
		return new SpanQueryPositionFilter(include, exclude, TextPatternPositionFilter.Operation.MATCHES, true);
	}

	@Override
	public BLSpanQuery or(QueryExecutionContext context, List<BLSpanQuery> clauses) {
		return new BLSpanOrQuery(clauses.toArray(new BLSpanQuery[] {}));
	}

	@Override
	public BLSpanQuery regex(QueryExecutionContext context, String value) {
		String valueNoStartEndMatch = value.replaceAll("\\^|\\$", "");
		try {
			return new BLSpanMultiTermQueryWrapper<>(new RegexpQuery(
					new Term(context.luceneField(), context.subpropPrefix() + context.optDesensitize(valueNoStartEndMatch))));
		} catch (StackOverflowError e) {
			// If we pass in a really large regular expression, like a huge
			// list of words combined with OR, stack overflow occurs inside
			// Lucene's automaton building code and we may end up here.
			throw new RegexpTooLargeException();
		}
	}

	@Override
	public BLSpanQuery sequence(QueryExecutionContext context, List<BLSpanQuery> clauses) {
		return new SpanQuerySequence(clauses);
	}

	@Override
	public BLSpanQuery docLevelAnd(QueryExecutionContext context, List<BLSpanQuery> clauses) {
		return new SpanQueryDocLevelAnd(clauses);
	}

	@Override
	public BLSpanQuery fuzzy(QueryExecutionContext context, String value, int maxEdits, int prefixLength) {
		String valuePrefix = context.subpropPrefix(); // for searching in "subproperties" (e.g. PoS features)
		prefixLength += valuePrefix.length();
		return new SpanFuzzyQuery(new Term(context.luceneField(), valuePrefix + context.optDesensitize(value)), maxEdits, prefixLength);
	}

	@Override
	public BLSpanQuery tags(QueryExecutionContext context, String elementName, Map<String, String> attr) {
		BLSpanQuery allTags;
		if (context.tagLengthInPayload()) {
			// Modern index, with tag length in payload
			allTags = new SpanQueryTags(context, elementName);
		} else {
			// Older index, with end tags stored in separate property
			allTags = new SpanQueryTagsOld(context, elementName);
		}
		if (attr == null || attr.isEmpty())
			return allTags;

		// Construct attribute filters
		List<BLSpanQuery> attrFilters = new ArrayList<>();
		QueryExecutionContext startTagContext = context.withProperty(ComplexFieldUtil.START_TAG_PROP_NAME);
		for (Map.Entry<String,String> e: attr.entrySet()) {
			String value = optInsensitive(context, "@" + e.getKey() + "__" + e.getValue());
			attrFilters.add(term(startTagContext, value));
		}

		// Filter the tags
		// (NOTE: only works for start tags and full elements because attribute values
		//  are indexed at the start tag!)
		BLSpanQuery filter = new SpanQueryAnd(attrFilters);
		return new SpanQueryPositionFilter(allTags, filter, TextPatternPositionFilter.Operation.STARTS_AT, false);
	}

	@Override
	public BLSpanQuery positionFilter(QueryExecutionContext context,
			BLSpanQuery producer, BLSpanQuery filter, Operation op, boolean invert,
			int leftAdjust, int rightAdjust) {
		return new SpanQueryPositionFilter(producer, filter, op, invert, leftAdjust, rightAdjust);
	}

	@Override
	public BLSpanQuery startsAt(QueryExecutionContext context, BLSpanQuery producer, BLSpanQuery filter) {
		return new SpanQueryPositionFilter(producer, filter, TextPatternPositionFilter.Operation.STARTS_AT, false);
	}

	@Override
	public BLSpanQuery endsAt(QueryExecutionContext context, BLSpanQuery producer, BLSpanQuery filter) {
		return new SpanQueryPositionFilter(producer, filter, TextPatternPositionFilter.Operation.ENDS_AT, false);
	}

	@Override
	public BLSpanQuery term(QueryExecutionContext context, String value) {
		// Use a BlackLabSpanTermQuery instead of default Lucene one
		// because we need to override getField() to only return the base field name,
		// not the complete field name with the property.
		return new BLSpanTermQuery(new Term(context.luceneField(), context.subpropPrefix() + context.optDesensitize(value)));
	}

	@Override
	public BLSpanQuery expand(QueryExecutionContext context, BLSpanQuery clause, boolean expandToLeft, int min, int max) {
		SpanQueryExpansion spanQueryExpansion = new SpanQueryExpansion(clause, expandToLeft, min, max);
		spanQueryExpansion.setIgnoreLastToken(context.alwaysHasClosingToken());
		return spanQueryExpansion;
	}

	@Override
	public BLSpanQuery filterNGrams(QueryExecutionContext context, BLSpanQuery clause, Operation op, int min, int max) {
		return new SpanQueryFilterNGrams(clause, op, min, max);
	}

	@Override
	public BLSpanQuery repetition(BLSpanQuery clause, int min, int max) {
		return new SpanQueryRepetition(clause, min, max);
	}

	@Override
	public BLSpanQuery docLevelAndNot(BLSpanQuery include, BLSpanQuery exclude) {
		return new SpanQueryDocLevelAndNot(include, exclude);
	}

	@Override
	public BLSpanQuery wildcard(QueryExecutionContext context, String value) {
		return new BLSpanMultiTermQueryWrapper<>(new WildcardQuery(new Term(context.luceneField(),
				context.subpropPrefix() + context.optDesensitize(value))));
	}

	@Override
	public BLSpanQuery prefix(QueryExecutionContext context, String value) {
		return new BLSpanMultiTermQueryWrapper<>(new PrefixQuery(new Term(context.luceneField(),
				context.subpropPrefix() + context.optDesensitize(value))));
	}

	@Override
	public BLSpanQuery not(QueryExecutionContext context, BLSpanQuery clause) {
		SpanQueryNot spanQueryNot = new SpanQueryNot(clause);
		spanQueryNot.setIgnoreLastToken(context.alwaysHasClosingToken());
		return spanQueryNot;
	}

	@Override
	public BLSpanQuery any(QueryExecutionContext context, int min, int max) {
		return new SpanQueryNGrams(context.alwaysHasClosingToken(), context.luceneField(), min, max);
		//return SpanQueryNot.matchAllTokens(context.alwaysHasClosingToken(), context.luceneField());
	}

	@Override
	public BLSpanQuery edge(BLSpanQuery clause, boolean rightEdge) {
		return new SpanQueryEdge(clause, rightEdge);
	}

	@Override
	public BLSpanQuery captureGroup(BLSpanQuery clause, String name) {
		return new SpanQueryCaptureGroup(clause, name);
	}

}
