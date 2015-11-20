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
 * A TextPattern matching at least one of its child clauses.
 */
public class TextPatternOr extends TextPatternCombiner {

	public TextPatternOr(TextPattern... clauses) {
		super(clauses);
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		List<T> chResults = new ArrayList<T>(clauses.size());
		for (TextPattern cl : clauses) {
			chResults.add(cl.translate(translator, context));
		}
		if (chResults.size() == 1)
			return chResults.get(0);
		return translator.or(context, chResults);
	}

	@Override
	public boolean matchesEmptySequence() {
		for (TextPattern cl: clauses) {
			if (cl.matchesEmptySequence())
				return true;
		}
		return false;
	}

	@Override
	public TextPattern rewrite() {

		// Flatten nested OR queries.
		// This doesn't change the query because the OR operator is associative.
		boolean anyRewritten = false;
		boolean hasNotChild = false;
		List<TextPattern> rewrittenCl = new ArrayList<TextPattern>();
		for (TextPattern child: clauses) {
			TextPattern rewritten = child.rewrite();
			if (rewritten instanceof TextPatternOr) {
				// Flatten.
				// Child OR operation we want to flatten into this OR operation.
				// Replace the child, incorporating its children into this OR operation.
				rewrittenCl.addAll(((TextPatternOr)rewritten).clauses);
				anyRewritten = true;
			} else {
				if (rewritten.isNegativeOnly())
					hasNotChild = true;
				// Just add it.
				rewrittenCl.add(rewritten);
				if (rewritten != child)
					anyRewritten = true;
			}
		}

		// Rewrites OR queries containing some NOT children into "NAND" queries.
		// This helps us isolate problematic subclauses which we can then rewrite to
		// more efficient NOTCONTAINING clauses.
		if (hasNotChild) {
			// At least one clause starts with NOT.
			// Node should be rewritten to AND. Invert all clauses.
			for (int i = 0; i < rewrittenCl.size(); i++) {
				rewrittenCl.set(i, rewrittenCl.get(i).inverted());
			}
			// Note extra rewrite at the end to make sure AND NOT structure is correctly built.
			return ((new TextPatternAndNot(rewrittenCl.toArray(new TextPattern[0]))).inverted()).rewrite();
		}

		if (anyRewritten) {
			// Some clauses were rewritten.
			return new TextPatternOr(rewrittenCl.toArray(new TextPattern[0]));
		}

		// Node need not be rewritten; return as-is
		return this;
	}
}
