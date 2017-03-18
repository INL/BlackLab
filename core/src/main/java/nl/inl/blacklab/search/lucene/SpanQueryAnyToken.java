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
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;

import nl.inl.blacklab.search.TextPatternAnyToken;
import nl.inl.blacklab.search.fimatch.NfaFragment;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.util.LuceneUtil;

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

	boolean alwaysHasClosingToken = true;

	String luceneField;

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
			SpanQueryAnyToken result = new SpanQueryAnyToken(min + tp.min, addRepetitionMaxValues(max, tp.max), luceneField);
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
	public BLSpanWeight createWeight(final IndexSearcher searcher, boolean needsScores) throws IOException {
		final int realMin = min == 0 ? 1 : min; // always rewritten unless the whole query is optional
		return new BLSpanWeight(SpanQueryAnyToken.this, searcher, null) {
			@Override
			public void extractTerms(Set<Term> terms) {
				// No terms
			}

			@Override
			public void extractTermContexts(Map<Term, TermContext> contexts) {
				// No terms
			}

			@Override
			public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
				return new SpansNGrams(alwaysHasClosingToken, context.reader(), luceneField, realMin, max);
			}
		};
	}

	@Override
	public String toString(String field) {
		return "ANYTOKEN(" + min + ", " + max + ")";
	}

	@Override
	public String getRealField() {
		return luceneField;
	}

	@Override
	public int hashCode() {
		return min + 31 * max + luceneField.hashCode() + (alwaysHasClosingToken ? 37 : 0);
	}

	public boolean getAlwaysHasClosingToken() {
		return alwaysHasClosingToken;
	}

	@Override
	public boolean hitsAllSameLength() {
		return min == max;
	}

	@Override
	public int hitsLengthMin() {
		return min;
	}

	@Override
	public int hitsLengthMax() {
		return max;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return hitsAllSameLength();
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return min == max;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return min == max;
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

	@Override
	public NfaFragment getNfa(ForwardIndexAccessor fiAccessor, int direction) {
		final int realMin = min == 0 ? 1 : min; // always rewritten unless the whole query is optional
		NfaState state = NfaState.anyToken(null);
		NfaFragment frag = new NfaFragment(state, Arrays.asList(state));
		if (realMin != 1 || max != 1) {
			frag.repeat(realMin, max);
		}
		return frag;
	}

	@Override
	public boolean canMakeNfa() {
		return true;
	}

	@Override
	public long estimatedNumberOfHits(IndexReader reader) {
		// Should be rewritten, and if not, it matches all positions in the index.
		int numberOfExpansionSteps = max < 0 ? 50 : max - min + 1;
		return LuceneUtil.getSumTotalTermFreq(reader, luceneField) * numberOfExpansionSteps;
	}

}
