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

/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public abstract class HitProperty {
	public abstract HitPropValue get(Hit result);

	/**
	 * Compares two hits on this property
	 * @param a first hit
	 * @param b second hit
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	public abstract int compare(Hit a, Hit b);

	public boolean needsConcordances() {
		return false;
	}

	public abstract String getName();
}
