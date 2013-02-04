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
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.ToStringUtils;

/**
 * A SpanQuery for an AND NOT query: <code>[word = "look" & pos != "VERB"]</code>
 * (find occurrences of the word "look" but exclude the verb form)
 */
public class SpanQueryAndNot extends SpanQuery {
	private SpanQuery[] clauses = null;

	public SpanQueryAndNot(SpanQuery include, SpanQuery exclude) {
		clauses = new SpanQuery[] { include, exclude };
	}

	@Override
	public Query rewrite(IndexReader reader) throws IOException {
		SpanQueryAndNot clone = null;

		for (int i = 0; i < clauses.length; i++) {
			SpanQuery c = clauses[i];
			SpanQuery query = (SpanQuery) c.rewrite(reader);
			if (query != c) { // clause rewrote: must clone
				if (clone == null)
					clone = (SpanQueryAndNot) clone();
				clone.clauses[i] = query;
			}
		}
		if (clone != null) {
			return clone; // some clauses rewrote
		}
		return this; // no clauses rewrote
	}

	@Override
	public String toString() {
		return this.toString(clauses[0].getField());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || this.getClass() != o.getClass())
			return false;

		final SpanQueryAndNot that = (SpanQueryAndNot) o;

		if (!clauses.equals(that.clauses))
			return false;

		return getBoost() == that.getBoost();
	}

	@Override
	public int hashCode() {
		int h = 0;
		h ^= clauses.hashCode();
		h ^= (h << 10) | (h >>> 23);
		h ^= Float.floatToRawIntBits(getBoost());
		return h;
	}

	/**
	 * @return name of search field
	 */
	@Override
	public String getField() {
		return clauses[0].getField();
	}

	/**
	 * Constructs a Spans object (consisting of WrappedTypedSpans and/or AndSpans objects) that
	 * contains all spans from the include clause in documents that don't contain the exclude
	 * spans.
	 *
	 * @param reader
	 *            the IndexReader
	 * @return the Spans object, or null on error
	 * @throws IOException
	 */
	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		Spans includespans = clauses[0].getSpans(reader);
		Spans excludespans = clauses[1].getSpans(reader);
		Spans combi = new SpansAndNot(includespans, excludespans);
		return combi;
	}

	/**
	 * Add all terms to the supplied set
	 *
	 * @param terms
	 *            the set the terms should be added to
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void extractTerms(Set terms) {
		for (SpanQuery element : clauses) {
			element.extractTerms(terms);
		}
	}

	@Override
	public String toString(String field) {
		return "spanAndNot([include=" + clauses[0].toString(field) + ", exclude="
				+ clauses[1].toString(field) + "])" + ToStringUtils.boost(getBoost());
	}

	public SpanQuery[] getClauses() {
		return clauses;
	}
}
