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
			// All negative clauses; should have been rewritten, but ok
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
	public TextPattern rewrite() {
		// Rewrites AND queries containing only NOT children into "NOR" queries.
		// This helps us isolate problematic subclauses which we can then rewrite to
		// more efficient NOTCONTAINING clauses.
		boolean anyRewritten = false;
		List<TextPattern> rewritten = new ArrayList<TextPattern>();
		List<TextPattern> rewrittenNot = new ArrayList<TextPattern>();
		for (int i = 0; i < clauses.size(); i++) {
			TextPattern child = clauses.get(i);
			TextPattern r = child.rewrite();
			if (r != child)
				anyRewritten = true;
			if (r instanceof TextPatternNot ) {
				rewrittenNot.add(r.inverted());
			} else {
				rewritten.add(r);
			}
		}
		for (int i = 0; i < clausesNot.size(); i++) {
			TextPattern child = clauses.get(i);
			TextPattern r = child.rewrite();
			if (r != child)
				anyRewritten = true;
			if (r instanceof TextPatternNot ) {
				rewritten.add(r.inverted());
			} else {
				rewrittenNot.add(r);
			}
		}
		if (rewritten.size() == 0) {
			// Node should be rewritten to OR.
			return new TextPatternNot(new TextPatternOr(rewrittenNot.toArray(new TextPattern[0])));
		}
		
		if (anyRewritten) {
			// Some clauses were rewritten.
			return new TextPatternAnd(rewritten, rewrittenNot);
		}
		
		// Node need not be rewritten; return as-is
		return this;
	}

}
