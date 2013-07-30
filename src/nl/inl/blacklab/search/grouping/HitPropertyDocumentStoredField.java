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
import nl.inl.blacklab.search.Searcher;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

/**
 * A hit property for grouping on a stored field in the corresponding Lucene document.
 */
public class HitPropertyDocumentStoredField extends HitProperty {
	IndexReader reader;

	String fieldName;

	private String friendlyName;

	public HitPropertyDocumentStoredField(String fieldName, Searcher searcher) {
		this(fieldName, fieldName, searcher);
	}

	public HitPropertyDocumentStoredField(String fieldName, String friendlyName, Searcher searcher) {
		reader = searcher.getIndexReader();
		this.fieldName = fieldName;
		this.friendlyName = friendlyName;
	}

	@Override
	public HitPropValueString get(Hit result) {
		try {
			Document d = reader.document(result.doc);
			String value = d.get(fieldName);
			if (value == null)
				value = "";
			return new HitPropValueString(value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int compare(Object a, Object b) {
		try {
			Document d = reader.document(((Hit)a).doc);
			String va = d.get(fieldName);
			if (va == null)
				va = "";
			if (va.length() == 0) // sort empty string at the end
				return 1;

			d = reader.document(((Hit)b).doc);
			String vb = d.get(fieldName);
			if (vb == null)
				vb = "";
			if (vb.length() == 0) // sort empty string at the end
				return -1;

			return HitPropValue.collator.compare(va, vb);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getName() {
		return friendlyName;
	}
}
