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
package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Searcher;

/**
 * A hit property for grouping on the text actually matched. Requires HitConcordances as input (so
 * we have the hit text available).
 */
public class HitPropertyHitText extends HitProperty {

	private String fieldName;

	private Terms terms;

	public HitPropertyHitText(Searcher searcher, String field) {
		super();
		this.terms = searcher.getTerms(field);
		this.fieldName = field;
	}

	public HitPropertyHitText(Searcher searcher) {
		this(searcher, searcher.getContentsField());
	}

	@Override
	public HitPropValueContextWords get(Hit result) {
		if (result.context == null) {
			throw new RuntimeException("Context not available in hits objects; cannot sort/group on context");
		}

		// Copy the desired part of the context
		int n = result.contextRightStart - result.contextHitStart;
		if (n <= 0)
			return new HitPropValueContextWords(terms, new int[0]);
		int[] dest = new int[n];
		System.arraycopy(result.context, result.contextHitStart, dest, 0, n);
		return new HitPropValueContextWords(terms, dest);
	}

	@Override
	public int compare(Hit a, Hit b) {
		// Compare the hit context for these two hits
		int ai = a.contextHitStart;
		int bi = b.contextHitStart;
		while (ai < a.contextRightStart && bi < b.contextRightStart) {
			int ac = a.context[ai];
			int bc = b.context[bi];
			if (ac != bc) {
				return ac - bc;
			}
			ai++;
			bi++;
		}
		// One or both ran out, and so far, they're equal.
		if (ai == a.contextRightStart) {
			if (bi != b.contextRightStart) {
				// b longer than a => a < b
				return -1;
			}
			return 0; // same length; a == b
		}
		return 1; // a longer than b => a > b
	}

	@Override
	public String needsContext() {
		return fieldName;
	}

	@Override
	public String getName() {
		return "hit text";
	}
}
