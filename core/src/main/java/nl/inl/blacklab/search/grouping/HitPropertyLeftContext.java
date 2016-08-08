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

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;

/**
 * A hit property for grouping on the context of the hit. Requires HitConcordances as input (so we
 * have the hit text available).
 */
public class HitPropertyLeftContext extends HitProperty {

	private String luceneFieldName;

	private String propName;

	private Terms terms;

	private boolean sensitive;

	private Searcher searcher;

	public HitPropertyLeftContext(Hits hits, String field, String property) {
		this(hits, field, property, hits.getSearcher().isDefaultSearchCaseSensitive());
	}

	public HitPropertyLeftContext(Hits hits, String field) {
		this(hits, field, null, hits.getSearcher().isDefaultSearchCaseSensitive());
	}

	public HitPropertyLeftContext(Hits hits) {
		this(hits, hits.getSearcher().getMainContentsFieldName(), hits.getSearcher().isDefaultSearchCaseSensitive());
	}

	public HitPropertyLeftContext(Hits hits, String field, String property, boolean sensitive) {
		super(hits);
		this.searcher = hits.getSearcher();
		if (property == null || property.length() == 0) {
			this.luceneFieldName = ComplexFieldUtil.mainPropertyField(searcher.getIndexStructure(), field);
			this.propName = ComplexFieldUtil.getDefaultMainPropName();
		} else {
			this.luceneFieldName = ComplexFieldUtil.propertyField(field, property);
			this.propName = property;
		}
		this.terms = searcher.getTerms(luceneFieldName);
		this.sensitive = sensitive;
	}

	public HitPropertyLeftContext(Hits hits, String field, boolean sensitive) {
		this(hits, field, null, sensitive);
	}

	public HitPropertyLeftContext(Hits hits, boolean sensitive) {
		this(hits, hits.getSearcher().getMainContentsFieldName(), sensitive);
	}

	@Override
	public HitPropValueContextWords get(int hitNumber) {
		int[] context = hits.getHitContext(hitNumber);
		int contextHitStart = context[Hits.CONTEXTS_HIT_START_INDEX];
		//int contextRightStart = context[Hits.CONTEXTS_RIGHT_START_INDEX];
		int contextLength = context[Hits.CONTEXTS_LENGTH_INDEX];

		// Copy the desired part of the context
		int n = contextHitStart;
		if (n <= 0)
			return new HitPropValueContextWords(hits, propName, new int[0], sensitive);
		int[] dest = new int[n];
		int contextStart = contextLength * contextIndices.get(0) + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
		System.arraycopy(context, contextStart, dest, 0, n);

		// Reverse the order of the array, because we want to sort from right to left
		for (int i = 0; i < n / 2; i++) {
			int o = n - 1 - i;
			// Swap values
			int t = dest[i];
			dest[i] = dest[o];
			dest[o] = t;
		}
		return new HitPropValueContextWords(hits, propName, dest, sensitive);
	}

	@Override
	public int compare(Object i, Object j) {
		//Hit a = hits.getByOriginalOrder((Integer)i);
		//Hit b = hits.getByOriginalOrder((Integer)j);
		int[] ca = hits.getHitContext((Integer)i);
		int caHitStart = ca[Hits.CONTEXTS_HIT_START_INDEX];
		int caLength = ca[Hits.CONTEXTS_LENGTH_INDEX];
		int[] cb = hits.getHitContext((Integer)j);
		int cbHitStart = cb[Hits.CONTEXTS_HIT_START_INDEX];
		int cbLength = cb[Hits.CONTEXTS_LENGTH_INDEX];

		// Compare the left context for these two hits, starting at the end
		int contextIndex = contextIndices.get(0);
		int ai = caHitStart - 1;
		int bi = cbHitStart - 1;
		while (ai >= 0 && bi >= 0) {
			int cmp = terms.compareSortPosition(ca[contextIndex * caLength + ai + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS],
					cb[contextIndex * cbLength + bi + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS], sensitive);
			if (cmp != 0)
				return reverse ? -cmp : cmp;
			ai--;
			bi--;
		}
		// One or both ran out, and so far, they're equal.
		if (ai < 0) {
			if (bi >= 0) {
				// b longer than a => a < b
				return reverse ? 1 : -1;
			}
			return 0; // same length; a == b
		}
		return reverse ? -1 : 1; // a longer than b => a > b
	}

	@Override
	public List<String> needsContext() {
		return Arrays.asList(luceneFieldName);
	}

	@Override
	public String getName() {
		return "left context";
	}

	@Override
	public String serialize() {
		String[] parts = ComplexFieldUtil.getNameComponents(luceneFieldName);
		String thePropName = parts.length > 1 ? parts[1] : "";
		return serializeReverse() + PropValSerializeUtil.combineParts("left", thePropName, sensitive ? "s" : "i");
	}

	public static HitPropertyLeftContext deserialize(Hits hits, String info) {
		String[] parts = PropValSerializeUtil.splitParts(info);
		String fieldName = hits.settings().concordanceField();
		String propName = parts[0];
		if (propName.length() == 0)
			propName = ComplexFieldUtil.getDefaultMainPropName();
		boolean sensitive = parts.length > 1 ? parts[1].equalsIgnoreCase("s") : true;
		if (fieldName == null || fieldName.length() == 0)
			return new HitPropertyLeftContext(hits, sensitive);
		return new HitPropertyLeftContext(hits, fieldName, propName, sensitive);
	}

}
