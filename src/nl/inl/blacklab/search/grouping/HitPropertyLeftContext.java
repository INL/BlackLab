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
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Searcher;

/**
 * A hit property for grouping on the context of the hit. Requires HitConcordances as input (so we
 * have the hit text available).
 */
public class HitPropertyLeftContext extends HitProperty {

	private String fieldName;

	private Terms terms;

	public HitPropertyLeftContext(Searcher searcher, String field, String property) {
		super();
		if (property == null || property.length() == 0)
			this.fieldName = ComplexFieldUtil.mainPropertyField(searcher.getIndexStructure(), field);
		else
			this.fieldName = ComplexFieldUtil.propertyField(field, property);
		this.terms = searcher.getTerms(fieldName);
	}

	public HitPropertyLeftContext(Searcher searcher, String field) {
		this(searcher, field, null);
	}

	public HitPropertyLeftContext(Searcher searcher) {
		this(searcher, searcher.getContentsFieldMainPropName());
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

		// Reverse the order of the array, because we want to sort from right to left
		for (int i = 0; i < n / 2; i++) {
			int o = n - 1 - i;
			// Swap values
			int t = dest[i];
			dest[i] = dest[o];
			dest[o] = t;
		}
		return new HitPropValueContextWords(terms, dest);
	}

	@Override
	public int compare(Object oa, Object ob) {
		Hit a = (Hit) oa, b = (Hit) ob;

		// Compare the left context for these two hits, starting at the end
		int ai = a.contextHitStart - 1;
		int bi = b.contextHitStart - 1;
		while (ai >= 0 && bi >= 0) {
			int ac = a.context[ai];
			int bc = b.context[bi];
			if (ac != bc) {
				// Found a difference; comparison finished.
				return ac - bc;
			}
			ai--;
			bi--;
		}
		// One or both ran out, and so far, they're equal.
		if (ai < 0) {
			if (bi >= 0) {
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
		return "left context";
	}

}
