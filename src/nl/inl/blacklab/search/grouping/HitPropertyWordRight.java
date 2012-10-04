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

import nl.inl.blacklab.search.Hit;
import nl.inl.util.Utilities;

/**
 * A hit property for grouping on the context of the hit. Requires HitConcordances as input (so we
 * have the hit text available).
 */
public class HitPropertyWordRight extends HitProperty {
	boolean lowerCase;

	public HitPropertyWordRight(boolean lowerCase) {
		super();
		this.lowerCase = lowerCase;
	}

	public HitPropertyWordRight() {
		this(false);
	}

	@Override
	public String get(Hit result) {
		if (result.conc == null) {
			throw new RuntimeException("Can only sort/group on context if results are concordances");
		}
		String rightContext = result.conc[2].trim();
		int indexOf = rightContext.indexOf(' ');
		String word = rightContext;
		if (indexOf >= 0)
			word = rightContext.substring(0, indexOf);
		return Utilities.xmlToSortable(word, lowerCase);
	}

	@Override
	public boolean needsConcordances() {
		return true;
	}

	@Override
	public String getName() {
		return "word right";
	}

}
