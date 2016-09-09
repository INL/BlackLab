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
import java.util.Collection;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * A base class for a SpanQuery with an array of clauses. Provides default implementations of some
 * abstract methods in SpanQuery.
 */

public abstract class BLSpanQueryAbstract extends BLSpanQuery {
	/**
	 * The field name for this query. The "base" part is only applicable when dealing with complex
	 * fields: the base field name of "contents" and "contents%pos" would both be "contents".
	 */
	String baseFieldName = "";

	protected BLSpanQuery[] clauses;

	public BLSpanQueryAbstract() {
		//
	}

	public BLSpanQueryAbstract(BLSpanQuery first, BLSpanQuery second) {
		clauses = new BLSpanQuery[2];
		clauses[0] = first;
		clauses[1] = second;
		determineBaseFieldName();
	}

	public BLSpanQueryAbstract(BLSpanQuery clause) {
		clauses = new BLSpanQuery[1];
		clauses[0] = clause;
		determineBaseFieldName();
	}

	public BLSpanQueryAbstract(Collection<BLSpanQuery> clauscol) {
		clauses = new BLSpanQuery[clauscol.size()];
		int k = 0;
		for (BLSpanQuery s : clauscol) {
			clauses[k++] = s;
		}
		determineBaseFieldName();
	}

	public BLSpanQueryAbstract(BLSpanQuery[] _clauses) {
		clauses = _clauses;
		determineBaseFieldName();
	}

	private void determineBaseFieldName() {
		if (clauses.length > 0) {
			baseFieldName = ComplexFieldUtil.getBaseName(clauses[0].getField());
			for (int i = 1; i < clauses.length; i++) {
				String f = ComplexFieldUtil.getBaseName(clauses[i].getField());
				if (!baseFieldName.equals(f))
					throw new RuntimeException("Mix of incompatible fields in query ("
							+ baseFieldName + " and " + f + ")");
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || this.getClass() != o.getClass())
			return false;

		final BLSpanQueryAbstract that = (BLSpanQueryAbstract) o;

		if (!clauses.equals(that.clauses))
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		int h = clauses.hashCode();
		h ^= (h << 10) | (h >>> 23);
		return h;
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

	protected BLSpanQuery[] rewriteClauses(IndexReader reader) throws IOException {
		BLSpanQuery[] rewritten = new BLSpanQuery[clauses.length];
		boolean someRewritten = false;
		for (int i = 0; i < clauses.length; i++) {
			BLSpanQuery c = clauses[i];
			BLSpanQuery query = c == null ? null : (BLSpanQuery) c.rewrite(reader);
			rewritten[i] = query;
			if (query != c)
				someRewritten = true;
		}
		return someRewritten ? rewritten : null;
	}

	public String clausesToString(String field) {
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < clauses.length; i++) {
			SpanQuery clause = clauses[i];
			buffer.append(clause.toString(field));
			if (i != clauses.length - 1) {
				buffer.append(", ");
			}
		}
		return buffer.toString();
	}
}
