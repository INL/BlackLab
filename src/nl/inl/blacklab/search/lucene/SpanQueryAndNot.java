/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
 * <p>
 * Een SpanQuery voor een AND NOT-constructie met twee clauses:
 *
 * <code>appel AND NOT peer</code>
 * </p>
 *
 * <p>
 * Bovenstaand voorbeeld levert de documenten waar wel 'appel' maar niet 'peer' in voorkomt, met als
 * spans de voorkomens van 'appel'
 * </p>
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
	 * @return naam van de zoekvelden
	 */
	@Override
	public String getField() {
		return clauses[0].getField();
	}

	/**
	 * Maakt een Spans object (bestaande uit WrappedTypedSpans en/of AndSpans objecten) dat alle
	 * spans van de te matchen documenten bevat (wel die van de include query, niet die van de
	 * exclude query).
	 *
	 * @param reader
	 *            de IndexReader
	 * @return het Spans object, of null in geval van fout
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
