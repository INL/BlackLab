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

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.TextPatternAnyToken;

/**
 * A SpanQuery matching a number of tokens without any restrictions.
 */
public class SpanQueryAnyToken extends BLSpanQuery {

	/*
	 * The minimum number of tokens in this stretch.
	 */
	protected int min;

	/*
	 * The maximum number of tokens in this stretch.
	 */
	protected int max;

	private boolean alwaysHasClosingToken = true;

	private String luceneField;

	public SpanQueryAnyToken(int min, int max, String luceneField) {
		this.min = min;
		this.max = max;
		this.luceneField = luceneField;
	}

	public void setAlwaysHasClosingToken(boolean alwaysHasClosingToken) {
		this.alwaysHasClosingToken = alwaysHasClosingToken;
	}

	@Override
	public boolean matchesEmptySequence() {
		return min == 0;
	}

	@Override
	public BLSpanQuery noEmpty() {
		if (min > 0)
			return this;
		SpanQueryAnyToken result = new SpanQueryAnyToken(1, max, luceneField);
		if (!alwaysHasClosingToken)
			result.setAlwaysHasClosingToken(false);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternAnyToken) {
			SpanQueryAnyToken tp = ((SpanQueryAnyToken) obj);
			return min == tp.min && max == tp.max;
		}
		return false;
	}

	@Override
	public BLSpanQuery combineWithPrecedingPart(BLSpanQuery previousPart, IndexReader reader) throws IOException {
		if (previousPart instanceof SpanQueryAnyToken) {
			SpanQueryAnyToken tp = (SpanQueryAnyToken)previousPart;
			SpanQueryAnyToken result = new SpanQueryAnyToken(min + tp.min, (max == -1 || tp.max == -1) ? -1 : max + tp.max, luceneField);
			if (!alwaysHasClosingToken)
				result.setAlwaysHasClosingToken(false);
			return result;
		} else if (previousPart instanceof SpanQueryExpansion) {
			SpanQueryExpansion tp = (SpanQueryExpansion) previousPart;
			if (!tp.isExpandToLeft()) {
				// Any token clause after expand to right; combine.
				SpanQueryExpansion result = new SpanQueryExpansion(tp.getClause(), tp.isExpandToLeft(), tp.getMinExpand() + min, (max == -1 || tp.getMaxExpand() == -1) ? -1 : tp.getMaxExpand() + max);
				result.setIgnoreLastToken(tp.ignoreLastToken);
				return result;
			}
		}
		BLSpanQuery combo = super.combineWithPrecedingPart(previousPart, reader);
		if (combo == null) {
			SpanQueryExpansion exp = new SpanQueryExpansion(previousPart, false, min, max);
			exp.setIgnoreLastToken(alwaysHasClosingToken);
			combo = exp;
		}
		return combo;
	}

	@Override
	public boolean hasConstantLength() {
		return min == max;
	}

	@Override
	public int getMinLength() {
		return min;
	}

	@Override
	public int getMaxLength() {
		return max;
	}

	@Override
	public SpanWeight createWeight(final IndexSearcher searcher, boolean needsScores) throws IOException {
		final int realMin = min == 0 ? 1 : min; // always rewritten unless the whole query is optional
		return new SpanWeight(SpanQueryAnyToken.this, searcher, null) {
			@Override
			public void extractTerms(Set<Term> terms) {
				// No terms
			}

			@Override
			public void extractTermContexts(Map<Term, TermContext> contexts) {
				// No terms
			}

			@Override
			public Spans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
				return new SpansNGrams(alwaysHasClosingToken, context.reader(), luceneField, realMin, max);
			}
		};
	}

	@Override
	public String toString(String field) {
		return "ANYTOKEN(" + min + ", " + max + ")";
	}

	@Override
	public String getField() {
		return ComplexFieldUtil.getBaseName(luceneField);
	}

	@Override
	public int hashCode() {
		return min + 31 * max + luceneField.hashCode() + (alwaysHasClosingToken ? 37 : 0);
	}

	public boolean getAlwaysHasClosingToken() {
		return alwaysHasClosingToken;
	}
}
