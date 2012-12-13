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
 * A hit property for grouping on the context of the hit. Requires HitConcordances as input (so we
 * have the hit text available).
 */
public class HitPropertyWordLeft extends HitProperty {

	private Terms terms;

	public HitPropertyWordLeft(Searcher searcher, String field) {
		this.terms = searcher.getTerms(field);
	}

	@Override
	public HitPropValueContextWord get(Hit result) {
		if (result.context == null) {
			throw new RuntimeException("Context not available in hits objects; cannot sort/group on context");
		}

		if (result.contextHitStart <= 0)
			return new HitPropValueContextWord(terms, -1);
		return new HitPropValueContextWord(terms, result.context[result.contextHitStart - 1]);
	}

	@Override
	public int compare(Hit a, Hit b) {
		if (a.contextHitStart <= 0)
			return b.contextHitStart <= 0 ? 0 : -1;
		if (b.contextHitStart <= 0)
			return 1;
		// Compare one word to the left of the hit
		return a.context[a.contextHitStart - 1] - b.context[b.contextHitStart - 1];
	}

	@Override
	public boolean needsConcordances() {
		return true;
	}

	@Override
	public String getName() {
		return "word left";
	}

}
