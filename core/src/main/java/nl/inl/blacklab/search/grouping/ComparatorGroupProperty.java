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

import java.text.Collator;
import java.util.Comparator;

class ComparatorGroupProperty implements Comparator<Group> {
	private GroupProperty prop;

	Collator collator;

	boolean sortReverse;

	public ComparatorGroupProperty(GroupProperty prop, boolean sortReverse, Collator collator) {
		this.prop = prop;
		this.collator = collator;
		this.sortReverse = prop.defaultSortDescending() ? !sortReverse : sortReverse;
	}

	@Override
	public int compare(Group first, Group second) {
		return sortReverse ? -prop.compare(first, second) : prop.compare(first, second);
	}

}
