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
import java.util.Arrays;
import java.util.List;

/**
 * AND query for combining different properties from a complex field.
 *
 * Note that when generating a SpanQuery, the Span start and end are also matched! Therefore only
 * two hits in the same document at the same start and end position will produce a match. This is
 * useful for e.g. selecting adjectives that start with a 'b' (queries on different property
 * (sub)fields that should apply to the same word).
 */
public class TextPatternAndNot extends TextPattern {

	protected List<TextPattern> include = new ArrayList<TextPattern>();

	protected List<TextPattern> exclude = new ArrayList<TextPattern>();

	public TextPatternAndNot(TextPattern... clauses) {
		for (TextPattern clause : clauses) {
			this.include.add(clause);
		}
	}

	public TextPatternAndNot(List<TextPattern> includeClauses, List<TextPattern> excludeClauses) {
		include.addAll(includeClauses);
		exclude.addAll(excludeClauses);
	}

	public void replaceClause(TextPattern oldClause, TextPattern... newClauses) {
		int i = include.indexOf(oldClause);
		include.remove(i);
		for (TextPattern newChild: newClauses) {
			include.add(i, newChild);
			i++;
		}
	}

	public void replaceClauseNot(TextPattern oldClause, TextPattern... newClauses) {
		int i = exclude.indexOf(oldClause);
		exclude.remove(i);
		for (TextPattern newChild: newClauses) {
			exclude.add(i, newChild);
			i++;
		}
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		List<T> chResults = new ArrayList<T>(include.size());
		for (TextPattern cl : include) {
			chResults.add(cl.translate(translator, context));
		}
		List<T> chResultsNot = new ArrayList<T>(exclude.size());
		for (TextPattern cl : exclude) {
			chResultsNot.add(cl.translate(translator, context));
		}
		if (chResults.size() == 1 && chResultsNot.size() == 0) {
			// Single positive clause
			return chResults.get(0);
		} else if (chResults.size() == 0) {
			// All negative clauses, so it's really just a NOT query. Should've been rewritten, but ok.
			return translator.not(context, translator.and(context, chResultsNot));
		}
		// Combination of positive and possibly negative clauses
		T include = chResults.size() == 1 ? chResults.get(0) : translator.and(context, chResults);
		if (chResultsNot.size() == 0)
			return include;
		T exclude = chResultsNot.size() == 1 ? chResultsNot.get(0) : translator.and(context, chResultsNot);
		return translator.andNot(context, include, exclude);
	}

	@Override
	public Object clone() {
		try {
			TextPatternAndNot clone = (TextPatternAndNot) super.clone();

			// copy list of children so we can modify it independently
			clone.include = new ArrayList<TextPattern>(include);
			clone.exclude = new ArrayList<TextPattern>(exclude);

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException("Clone not supported: " + e.getMessage());
		}
	}

	@Override
	public TextPattern inverted() {
		if (exclude.size() == 0) {
			// In this case, it's better to just wrap this in TextPatternNot,
			// so it will be recognized by other rewrite()s.
			return super.inverted();
		}
		return new TextPatternAndNot(exclude, include);
	}

	@Override
	protected boolean okayToInvertForOptimization() {
		// Inverting is "free" if it will still be an AND NOT query (i.e. will have a positive component).
		return exclude.size() > 0;
	}

	@Override
	public boolean isNegativeOnly() {
		return include.size() == 0;
	}

	@Override
	public TextPattern rewrite() {

		// Flatten nested AND queries, and invert negative-only clauses.
		// This doesn't change the query because the AND operator is associative.
		boolean anyRewritten = false;
		List<TextPattern> rewrittenCl = new ArrayList<TextPattern>();
		List<TextPattern> rewrittenNotCl = new ArrayList<TextPattern>();
		boolean isNot = false;
		for (List<TextPattern> cl: Arrays.asList(include, exclude)) {
			for (TextPattern child: cl) {
				List<TextPattern> clPos = isNot ? rewrittenNotCl : rewrittenCl;
				List<TextPattern> clNeg = isNot ? rewrittenCl : rewrittenNotCl;
				TextPattern rewritten = child.rewrite();
				if (!(rewritten instanceof TextPatternAndNot) && rewritten.isNegativeOnly()) {
					// "Switch sides": invert the clause, and
					// swap the lists we add clauses to.
					rewritten = rewritten.inverted();
					List<TextPattern> temp = clPos;
					clPos = clNeg;
					clNeg = temp;
					anyRewritten = true;
				}
				if (rewritten instanceof TextPatternAndNot) {
					// Flatten.
					// Child AND operation we want to flatten into this AND operation.
					// Replace the child, incorporating its children into this AND operation.
					clPos.addAll(((TextPatternAndNot)rewritten).include);
					clNeg.addAll(((TextPatternAndNot)rewritten).exclude);
					anyRewritten = true;
				} else {
					// Just add it.
					clPos.add(rewritten);
					if (rewritten != child)
						anyRewritten = true;
				}
			}
			isNot = true;
		}

		if (rewrittenCl.size() == 0) {
			// All-negative; node should be rewritten to OR.
			if (rewrittenNotCl.size() == 1)
				return rewrittenCl.get(0).inverted();
			return (new TextPatternOr(rewrittenNotCl.toArray(new TextPattern[0]))).inverted();
		}

		if (anyRewritten) {
			// Some clauses were rewritten.
			return new TextPatternAndNot(rewrittenCl, rewrittenNotCl);
		}

		// Node need not be rewritten; return as-is
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternAndNot) {
			return include.equals(((TextPatternAndNot) obj).include) &&
					exclude.equals(((TextPatternAndNot) obj).exclude);
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		if (include.size() == 0)
			return true;
		return include.get(0).hasConstantLength();
	}

	@Override
	public int getMinLength() {
		if (include.size() == 0)
			return 1;
		return include.get(0).getMinLength();
	}

	@Override
	public int getMaxLength() {
		if (include.size() == 0)
			return 1;
		return include.get(0).getMaxLength();
	}

	@Override
	public int hashCode() {
		return include.hashCode() + exclude.hashCode();
	}
}
