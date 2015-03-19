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


/**
 * Abstract base class for a property of a hit, like document title, hit text, right context, etc.
 */
public class GroupPropertySize extends GroupProperty {
	@Override
	public HitPropValueInt get(Group result) {
		return new HitPropValueInt(((HitGroup)result).size());
	}

	@Override
	public int compare(Group a, Group b) {
		if (reverse)
			return ((HitGroup)b).size() - ((HitGroup)a).size();
		return ((HitGroup)a).size() - ((HitGroup)b).size();
	}

	@Override
	public boolean defaultSortDescending() {
		return !reverse;
	}

	@Override
	public String serialize() {
		return serializeReverse() + "size";
	}

}
