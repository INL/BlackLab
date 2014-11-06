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

import nl.inl.blacklab.search.grouping.HitPropValueString;
import nl.inl.blacklab.search.grouping.PropValSerializeUtil;

/**
 * For grouping DocResult objects by the value of a stored field in the Lucene documents. The field
 * name is given when instantiating this class, and might be "author", "year", and such.
 */
public class DocPropertyStoredField extends DocProperty {
	private String fieldName;
	private String friendlyName;

	public DocPropertyStoredField(String fieldName) {
		this(fieldName, fieldName);
	}

	public DocPropertyStoredField(String fieldName, String friendlyName) {
		this.fieldName = fieldName;
		this.friendlyName = friendlyName;
	}

	@Override
	public HitPropValueString get(DocResult result) {
		return new HitPropValueString(result.getDocument().get(fieldName));
	}

	/**
	 * Compares two docs on this property
	 * @param a first doc
	 * @param b second doc
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	@Override
	public int compare(DocResult a, DocResult b) {
		String sa = a.getDocument().get(fieldName);
		if (sa == null)
			sa = "";
		String sb = b.getDocument().get(fieldName);
		if (sb == null)
			sb = "";
		if (sa.length() == 0) // sort empty string at the end
			return sb.length() == 0 ? 0 : (reverse ? -1 : 1);
		if (sb.length() == 0) // sort empty string at the end
			return reverse ? 1 : -1;
		return reverse ? sb.compareTo(sa) : sa.compareTo(sb);
	}

	@Override
	public String getName() {
		return friendlyName;
	}

	public static DocPropertyStoredField deserialize(String info) {
		return new DocPropertyStoredField(info);
	}

	@Override
	public String serialize() {
		return serializeReverse() + PropValSerializeUtil.combineParts("field", fieldName);
	}

}
