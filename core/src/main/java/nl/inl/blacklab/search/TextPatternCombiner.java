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

/**
 * Abstract base class for combining several text patterns into a single new compound TextPattern
 */
public abstract class TextPatternCombiner extends TextPattern {
	protected List<TextPattern> clauses = new ArrayList<>();

	public TextPatternCombiner(TextPattern... clauses) {
		for (TextPattern clause : clauses) {
			addClause(clause);
		}
	}

	public int numberOfClauses() {
		return clauses.size();
	}

	@Override
	public abstract BLSpanQuery translate(QueryExecutionContext context);

	public void addClause(TextPattern clause) {
		clauses.add(clause);
	}

	@Override
	public Object clone() {
		try {
			TextPatternCombiner clone = (TextPatternCombiner) super.clone();

			// copy list of children so we can modify it independently
			clone.clauses = new ArrayList<>(clauses);

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new UnsupportedOperationException("Clone not supported: " + e.getMessage());
		}
	}

	public void replaceClause(TextPattern oldClause, TextPattern... newClauses) {
		int i = clauses.indexOf(oldClause);
		clauses.remove(i);
		for (TextPattern newChild: newClauses) {
			clauses.add(i, newChild);
			i++;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternCombiner) {
			return clauses.equals(((TextPatternCombiner) obj).clauses);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return clauses.hashCode();
	}
}
