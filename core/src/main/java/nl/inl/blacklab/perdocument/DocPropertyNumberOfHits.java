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
package nl.inl.blacklab.perdocument;

import nl.inl.blacklab.search.grouping.HitPropValueInt;

/**
 * For grouping DocResult objects on the number of hits. This would put documents with 1 hit in a
 * group, documents with 2 hits in another group, etc.
 */
public class DocPropertyNumberOfHits extends DocProperty {
	@Override
	public HitPropValueInt get(DocResult result) {
		return new HitPropValueInt(result.getNumberOfHits());
	}

	/**
	 * Compares two docs on this property
	 * @param a first doc
	 * @param b second doc
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	@Override
	public int compare(DocResult a, DocResult b) {
		if (reverse)
			return b.getNumberOfHits() - a.getNumberOfHits();
		return a.getNumberOfHits() - b.getNumberOfHits();
	}

	@Override
	public boolean defaultSortDescending() {
		return !reverse;
	}

	@Override
	public String getName() {
		return "number of hits";
	}

	public static DocPropertyNumberOfHits deserialize() {
		return new DocPropertyNumberOfHits();
	}

	@Override
	public String serialize() {
		return serializeReverse() + "numhits";
	}

}
