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
 * AND query for combining different properties from a complex field.
 *
 * Note that when generating a SpanQuery, the Span start and end are also matched! Therefore only
 * two hits in the same document at the same start and end position will produce a match. This is
 * useful for e.g. selecting adjectives that start with a 'b' (queries on different property
 * (sub)fields that should apply to the same word).
 */
public class TextPatternAnd extends TextPattern {
	
	protected List<TextPattern> clauses = new ArrayList<TextPattern>();
	
	protected List<TextPattern> clausesNot = new ArrayList<TextPattern>();

	public TextPatternAnd(TextPattern... clauses) {
		for (TextPattern clause : clauses) {
			this.clauses.add(clause);
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

	public void replaceClauseNot(TextPattern oldClause, TextPattern... newClauses) {
		int i = clausesNot.indexOf(oldClause);
		clausesNot.remove(i);
		for (TextPattern newChild: newClauses) {
			clausesNot.add(i, newChild);
			i++;
		}
	}

	public TextPatternAnd(List<TextPattern> includeClauses, List<TextPattern> excludeClauses) {
		clauses.addAll(includeClauses);
		clausesNot.addAll(excludeClauses);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		List<T> chResults = new ArrayList<T>(clauses.size());
		for (TextPattern cl : clauses) {
			chResults.add(cl.translate(translator, context));
		}
		List<T> chResultsNot = new ArrayList<T>(clausesNot.size());
		for (TextPattern cl : clausesNot) {
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
			TextPatternAnd clone = (TextPatternAnd) super.clone();

			// copy list of children so we can modify it independently
			clone.clauses = new ArrayList<TextPattern>(clauses);
			clone.clausesNot = new ArrayList<TextPattern>(clausesNot);

			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException("Clone not supported: " + e.getMessage());
		}
	}
	
	@Override
	public TextPattern inverted() {
		if (clausesNot.size() == 0) {
			// In this case, it's better to just wrap this in TextPatternNot,
			// so it will be recognized by other rewrite()s.
			return super.inverted();
		}
		return new TextPatternAnd(clausesNot, clauses);
	}
	
	@Override
	boolean okayToInvertForOptimization() {
		// Inverting is "free" if it will still be an AND NOT query (i.e. will have a positive component).
		return clausesNot.size() > 0;
	}
	
	@Override
	boolean isNegativeOnly() {
		return clauses.size() == 0;
	}

	@Override
	public TextPattern rewrite() {

		// Flatten nested AND queries.
		// This doesn't change the query because the sequence operator is associative.
		boolean anyRewritten = false;
		List<TextPattern> rewrittenCl = new ArrayList<TextPattern>();
		List<TextPattern> rewrittenNotCl = new ArrayList<TextPattern>();
		for (TextPattern child: clauses) {
			TextPattern rewritten = child.rewrite();
			if (rewritten instanceof TextPatternAnd) {
				// Flatten.
				// Child sequence we want to flatten into this sequence.
				// Replace the child, incorporating the child sequence into the rewritten sequence
				rewrittenCl.addAll(((TextPatternAnd)rewritten).clauses);
				rewrittenNotCl.addAll(((TextPatternAnd)rewritten).clausesNot);
				anyRewritten = true;
			} else if (rewritten.okayToInvertForOptimization()) {
				// "Switch sides"
				rewrittenNotCl.add(rewritten.inverted());
				anyRewritten = true;
			} else {
				// Just add it.
				rewrittenCl.add(rewritten);
				if (rewritten != child)
					anyRewritten = true;
			}
		}
		for (TextPattern child: clausesNot) {
			TextPattern rewritten = child.rewrite();
			if (rewritten instanceof TextPatternAnd) {
				// Flatten.
				// Child sequence we want to flatten into this sequence.
				// Replace the child, incorporating the child sequence into the rewritten sequence
				rewrittenCl.addAll(((TextPatternAnd)rewritten).clausesNot);
				rewrittenNotCl.addAll(((TextPatternAnd)rewritten).clauses);
				anyRewritten = true;
			} else if (rewritten.okayToInvertForOptimization()) {
				// "Switch sides"
				rewrittenCl.add(rewritten.inverted());
				anyRewritten = true;
			} else {
				// Just add it.
				rewrittenNotCl.add(rewritten);
				if (rewritten != child)
					anyRewritten = true;
			}
		}
		
		if (rewrittenCl.size() == 0) {
			// All-negative; node should be rewritten to OR.
			return (new TextPatternOr(rewrittenNotCl.toArray(new TextPattern[0]))).inverted();
		}
		
		if (anyRewritten) {
			// Some clauses were rewritten.
			return new TextPatternAnd(rewrittenCl, rewrittenNotCl);
		}
		
		// Node need not be rewritten; return as-is
		return this;
	}

}
