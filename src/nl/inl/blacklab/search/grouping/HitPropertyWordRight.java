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
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Searcher;

/**
 * A hit property for grouping on the context of the hit. Requires HitConcordances as input (so we
 * have the hit text available).
 */
public class HitPropertyWordRight extends HitProperty {

	private String fieldName;

	private Terms terms;

	public HitPropertyWordRight(Searcher searcher, String field, String property) {
		super();
		if (property == null || property.length() == 0)
			this.fieldName = ComplexFieldUtil.mainPropertyField(searcher.getIndexStructure(), field);
		else
			this.fieldName = ComplexFieldUtil.propertyField(field, property);
		this.terms = searcher.getTerms(fieldName);
	}

	public HitPropertyWordRight(Searcher searcher, String field) {
		this(searcher, field, null);
	}

	public HitPropertyWordRight(Searcher searcher) {
		this(searcher, searcher.getContentsFieldMainPropName());
	}

	@Override
	public HitPropValueContextWord get(Hit result) {
		if (result.context == null) {
			throw new RuntimeException("Context not available in hits objects; cannot sort/group on context");
		}

		if (result.context.length <= result.contextRightStart)
			return new HitPropValueContextWord(terms, -1);
		int contextStart = result.contextLength * contextIndices.get(0);
		return new HitPropValueContextWord(terms, result.context[contextStart + result.contextRightStart]);
	}

	@Override
	public int compare(Object oa, Object ob) {
		Hit a = (Hit) oa, b = (Hit) ob;

		if (a.context.length <= a.contextRightStart)
			return b.context.length <= b.contextRightStart ? 0 : -1;
		if (b.context.length <= b.contextRightStart)
			return 1;
		// Compare one word to the right of the hit
		int contextIndex = contextIndices.get(0);
		return a.context[contextIndex * a.contextLength + a.contextRightStart]
				- b.context[contextIndex * b.contextLength + b.contextRightStart];
	}

	@Override
	public List<String> needsContext() {
		return Arrays.asList(fieldName);
	}

	@Override
	public String getName() {
		return "word right";
	}

}
