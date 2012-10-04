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

import java.text.Collator;
import java.util.Comparator;

import nl.inl.util.Utilities;

public class ComparatorDocProperty implements Comparator<DocResult> {
	private DocProperty prop;

	Collator collator;

	boolean sortReverse;

	public ComparatorDocProperty(DocProperty prop, Collator collator) {
		this.prop = prop;
		this.collator = collator;
		sortReverse = prop.defaultSortDescending();
	}

	@Override
	public int compare(DocResult first, DocResult second) {
		String a = Utilities.sanitizeForSorting(prop.get(first));
		String b = Utilities.sanitizeForSorting(prop.get(second));
		if (sortReverse)
			return collator.compare(b, a);
		return collator.compare(a, b);
	}

}
