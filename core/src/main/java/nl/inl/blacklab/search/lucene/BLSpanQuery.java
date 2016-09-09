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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * A required interface for a BlackLab SpanQuery. All our queries must be
 * derived from this so we know they will produce BLSpans (which
 * contains extra methods for optimization).
 */
public abstract class BLSpanQuery extends SpanQuery {

	@Override
	public abstract String toString(String field);

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object obj);

	/**
	 * Does this query match the empty sequence?
	 *
	 * For example, the query [word="cow"]* matches the empty sequence. We need to know this so we
	 * can rewrite to the appropriate queries. A query of the form "AB*" would be translated into
	 * "A|AB+", so each component of the query actually generates non-empty matches.
	 *
	 * We default to no because most queries don't match the empty sequence.
	 *
	 * @return true if this pattern matches the empty sequence, false otherwise
	 */
	public boolean matchesEmptySequence() {
		return false;
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		return this;
	}

}
