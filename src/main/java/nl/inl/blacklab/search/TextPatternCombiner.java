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

/**
 * Abstract base class for combining several text patterns into a single new compound TextPattern
 */
public abstract class TextPatternCombiner extends TextPattern {
	protected List<TextPattern> clauses = new ArrayList<TextPattern>();

	public TextPatternCombiner(TextPattern... clauses) {
		for (TextPattern clause : clauses) {
			addClause(clause);
		}
	}

	public int numberOfClauses() {
		return clauses.size();
	}

	@Override
	public abstract <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context);

	public void addClause(TextPattern clause) {
		clauses.add(clause);
	}

	@Override
	public Object clone() {
		try {
			TextPatternCombiner clone = (TextPatternCombiner) super.clone();

			// copy list of children so we can modify it independently
			clone.clauses = new ArrayList<TextPattern>(clauses);

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException("Clone not supported: " + e.getMessage());
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

	/**
	 * Rewrites the query by calling rewrite() on its children and, if any of its
	 * children are rewritten, cloning this with the rewritten children.
	 * @return the rewritten query (or, if no rewriting was necessary, this)
	 */
	@Override
	public TextPattern rewrite() {
		TextPatternCombiner clone = null;
		for (TextPattern child : clauses) {
			TextPattern rewritten = child.rewrite();
			if (rewritten != child) {
				if (clone == null)
					clone = (TextPatternCombiner) clone();
				clone.replaceClause(child, rewritten);
			}
		}
		if (clone != null) {
			return clone;
		}
		return this;
	}

}
