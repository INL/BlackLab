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

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.grouping.HitPropValue;

/**
 * A value plus a count for a certain group of documents
 * that have some property value in common. Used for faceted
 * search.
 */
public class DocCount extends DocGroup {

	private Integer count;

	public DocCount(Searcher searcher, HitPropValue groupIdentity) {
		super(searcher, groupIdentity);
		count = 0;
	}

	public DocCount(Searcher searcher, HitPropValue groupIdentity, int count) {
		super(searcher, groupIdentity);
		this.count = count;
	}

	@Override
	public DocResults getResults() {
		throw new UnsupportedOperationException("DocCount has no results objects!");
	}

	@Override
	public int size() {
		return count;
	}

	public void increment() {
		count++;
	}

}
