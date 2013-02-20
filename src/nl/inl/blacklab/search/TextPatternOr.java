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
	public <T> T translate(TextPatternTranslator<T> translator, TPTranslationContext context) {
		List<T> chResults = new ArrayList<T>(clauses.size());
		for (TextPattern cl : clauses) {
			chResults.add(cl.translate(translator, context));
		}
		if (chResults.size() == 1)
			return chResults.get(0);
		return translator.or(context, chResults);
	}

	/*

	NOTE: this code rewrites OR queries containing one or more NOT children
	into AND queries. It is not currently used but we may want to use something
	like this in the future to better optimize certain queries, so we'll leave
	it here for now.

	@Override
	public TextPattern rewrite() {
		boolean hasNotChild = false;
		TextPattern[] rewritten = new TextPattern[clauses.size()];
		for (int i = 0; i < clauses.size(); i++) {
			TextPattern child = clauses.get(i);
			rewritten[i] = child.rewrite();
			if (rewritten[i] instanceof TextPatternNot) {
				hasNotChild = true;
				break;
			}
		}
		if (hasNotChild) {
			// At least one clause starts with NOT.
			// Node should be rewritten to AND. Invert all clauses.
			for (int i = 0; i < rewritten.length; i++) {
				rewritten[i] = rewritten[i].inverted();
			}
			return new TextPatternNot(new TextPatternAnd(rewritten));
		}
		// Node need not be rewritten; return as-is
		return this;
	}

	 */
}
