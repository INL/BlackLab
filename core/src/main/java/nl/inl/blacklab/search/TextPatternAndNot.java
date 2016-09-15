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
package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;

/**
 * AND (NOT) query for combining different properties from a complex field.
 *
 * Note that when generating a SpanQuery, the Span start and end are also matched! Therefore only
 * two hits in the same document at the same start and end position will produce a match. This is
 * useful for e.g. selecting adjectives that start with a 'b' (queries on different property
 * (sub)fields that should apply to the same word).
 */
public class TextPatternAndNot extends TextPattern {

	protected List<TextPattern> include = new ArrayList<>();

	protected List<TextPattern> exclude = new ArrayList<>();

	public TextPatternAndNot(TextPattern... clauses) {
		for (TextPattern clause : clauses) {
			this.include.add(clause);
		}
	}

	public TextPatternAndNot(List<TextPattern> includeClauses, List<TextPattern> excludeClauses) {
		include.addAll(includeClauses);
		exclude.addAll(excludeClauses);
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		List<BLSpanQuery> chResults = new ArrayList<>(include.size());
		for (TextPattern cl : include) {
			chResults.add(cl.translate(context));
		}
		List<BLSpanQuery> chResultsNot = new ArrayList<>(exclude.size());
		for (TextPattern cl : exclude) {
			chResultsNot.add(cl.translate(context));
		}
		if (chResults.size() == 1 && chResultsNot.isEmpty()) {
			// Single positive clause
			return chResults.get(0);
		} else if (chResults.isEmpty()) {
			// All negative clauses, so it's really just a NOT query.
			SpanQueryNot spanQueryNot = new SpanQueryNot(new SpanQueryAnd(chResultsNot));
			spanQueryNot.setIgnoreLastToken(context.alwaysHasClosingToken());
			return spanQueryNot;
		}
		// Combination of positive and possibly negative clauses
		BLSpanQuery includeResult = chResults.size() == 1 ? chResults.get(0) : new SpanQueryAnd(chResults);
		if (chResultsNot.isEmpty())
			return includeResult;
		BLSpanQuery excludeResult = chResultsNot.size() == 1 ? chResultsNot.get(0) : new SpanQueryAnd(chResultsNot);
		return new SpanQueryPositionFilter(includeResult, excludeResult, SpanQueryPositionFilter.Operation.MATCHES, true);
	}

	@Override
	public Object clone() {
		try {
			TextPatternAndNot clone = (TextPatternAndNot) super.clone();

			// copy list of children so we can modify it independently
			clone.include = new ArrayList<>(include);
			clone.exclude = new ArrayList<>(exclude);

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new UnsupportedOperationException("Clone not supported: " + e.getMessage());
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternAndNot) {
			return include.equals(((TextPatternAndNot) obj).include) &&
					exclude.equals(((TextPatternAndNot) obj).exclude);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return include.hashCode() + exclude.hashCode();
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		if (exclude.isEmpty())
			return "AND(" + clausesToString(include, context) + ")";
		return "ANDNOT([" + clausesToString(include, context) + "], [" + clausesToString(exclude, context) + "])";
	}

	@Override
	public String toString() {
		if (exclude.isEmpty())
			return "AND(" + clausesToString(include) + ")";
		return "ANDNOT([" + clausesToString(include) + "], [" + clausesToString(exclude) + "])";
	}

}
