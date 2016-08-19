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
package nl.inl.blacklab.search.sequences;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.search.lucene.SpanQueryBase;

/**
 * Finds repeated consecutive hits.
 *
 * This generates all possible sequences of consecutive hits, so if we search
 * for B+ in the input string ABBBA, we'll get 3 hits of length 1, 2 hits of length 2,
 * and 1 hit of length 3. In the future, this should be made configurable (to specifically
 * support greedy/reluctant matching, etc.)
 */
public class SpanQueryRepetition extends SpanQueryBase {
	private int min;

	private int max;

	public SpanQueryRepetition(SpanQuery clause, int min, int max) {
		super(clause);
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new IllegalArgumentException("min > max");
		if (min < 1)
			throw new IllegalArgumentException("min < 1");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!super.equals(o))
			return false;

		final SpanQueryRepetition that = (SpanQueryRepetition) o;
		return min == that.min && max == that.max;
	}

	@Override
	public Spans getSpans(LeafReaderContext context, Bits acceptDocs, Map<Term,TermContext> termContexts) throws IOException {
		Spans spans = clauses[0].getSpans(context, acceptDocs, termContexts);
		if (spans == null)
			return null;
		return new SpansRepetition(spans, min, max);
	}

	@Override
	public int hashCode() {
		int h = clauses.hashCode();
		h ^= (h << 10) | (h >>> 23);
		h ^= min << 10;
		h ^= max << 5;
		h ^= Float.floatToRawIntBits(getBoost());
		return h;
	}

	@Override
	public String toString(String field) {
		return "SpanQueryRepetition(" + clauses[0] + ", " + min + ", " + max + ")";
	}
}
