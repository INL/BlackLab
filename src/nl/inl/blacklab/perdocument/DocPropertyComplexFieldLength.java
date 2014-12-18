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

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.grouping.HitPropValueInt;
import nl.inl.blacklab.search.grouping.PropValSerializeUtil;

/**
 * Retrieves the length of a complex field (i.e. the main "contents" field)
 * in tokens.
 */
public class DocPropertyComplexFieldLength extends DocProperty {

	private String friendlyName;

	private String fieldName;

	public DocPropertyComplexFieldLength(String fieldName, String friendlyName) {
		this.fieldName = ComplexFieldUtil.lengthTokensField(fieldName);
		this.friendlyName = friendlyName;
	}

	public DocPropertyComplexFieldLength(String fieldName) {
		this(fieldName, fieldName + " length");
	}

	@Override
	public HitPropValueInt get(DocResult result) {
		try {
			int subtractFromLength = 1; // TODO: check IndexStructure.alwaysHasClosingToken() to see if we really should subtract 1
			int length = Integer.parseInt(result.getDocument().get(fieldName)) - subtractFromLength;
			return new HitPropValueInt(length);
		} catch (NumberFormatException e) {
			return new HitPropValueInt(0);
		}
	}

	/**
	 * Compares two docs on this property
	 * @param a first doc
	 * @param b second doc
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	@Override
	public int compare(DocResult a, DocResult b) {
		try {
			int ia = Integer.parseInt(a.getDocument().get(fieldName));
			int ib = Integer.parseInt(b.getDocument().get(fieldName));
			return reverse ? ib - ia : ia - ib;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	public String getName() {
		return friendlyName;
	}

	public static DocPropertyComplexFieldLength deserialize(String info) {
		return new DocPropertyComplexFieldLength(info);
	}

	@Override
	public String serialize() {
		return serializeReverse() + PropValSerializeUtil.combineParts("fieldlen", fieldName);
	}

}
