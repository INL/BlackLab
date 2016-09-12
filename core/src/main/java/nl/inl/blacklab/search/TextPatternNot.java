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

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryNot;

/**
 * NOT operator for TextPattern queries at token and sequence level.
 * Really only makes sense for 1-token clauses, as it produces all tokens
 * that don't match the clause.
 */
public class TextPatternNot extends TextPatternCombiner {
	public TextPatternNot(TextPattern clause) {
		super(clause);
	}

	@Override
	public BLSpanQuery translate(QueryExecutionContext context) {
		SpanQueryNot spanQueryNot = new SpanQueryNot(clauses.get(0).translate(context));
		spanQueryNot.setIgnoreLastToken(context.alwaysHasClosingToken());
		return spanQueryNot;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternNot) {
			return super.equals(obj);
		}
		return false;
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "NOT(" + clauses.get(0).toString(context) + ")";
	}

	@Override
	public String toString() {
		return "NOT(" + clauses.get(0).toString() + ")";
	}
}
