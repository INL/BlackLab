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
import nl.inl.blacklab.search.Hits;

/**
 * A hit property for grouping per document.
 */
public class HitPropertyDocumentId extends HitProperty {

	public HitPropertyDocumentId(Hits hits) {
		super(hits);
	}

	@Override
	public HitPropValueInt get(int hitNumber) {
		Hit result = hits.getByOriginalOrder(hitNumber);
		return new HitPropValueInt(result.doc);
	}

	@Override
	public String getName() {
		return "document id";
	}

	@Override
	public int compare(Object i, Object j) {
		Hit a = hits.getByOriginalOrder((Integer)i);
		Hit b = hits.getByOriginalOrder((Integer)j);
		return reverse ? b.doc - a.doc : a.doc - b.doc;
	}

	@Override
	public String serialize() {
		return serializeReverse() + "docid";
	}

	public static HitPropertyDocumentId deserialize(Hits hits) {
		return new HitPropertyDocumentId(hits);
	}
}
