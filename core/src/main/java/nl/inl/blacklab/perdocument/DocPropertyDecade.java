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

import nl.inl.blacklab.search.grouping.HitPropValueDecade;
import nl.inl.blacklab.search.grouping.HitPropertyDocumentDecade;
import nl.inl.blacklab.search.grouping.PropValSerializeUtil;

/**
 * For grouping DocResult objects by decade based on a
 * stored field containing a year.
 */
public class DocPropertyDecade extends DocProperty {

	private String fieldName;

	public DocPropertyDecade(String fieldName) {
		this.fieldName = fieldName;
	}

	@Override
	public HitPropValueDecade get(DocResult result) {
		String strYear = result.getDocument().get(fieldName);
		int year;
		try {
			year = Integer.parseInt(strYear);
			year -= year % 10;
		} catch (NumberFormatException e) {
			year = HitPropertyDocumentDecade.UNKNOWN_VALUE;
		}
		return new HitPropValueDecade(year);
	}

	/**
	 * Compares two docs on this property
	 * @param a first doc
	 * @param b second doc
	 * @return 0 if equal, negative if a < b, positive if a > b.
	 */
	@Override
	public int compare(DocResult a, DocResult b) {
		String strYearA = a.getDocument().get(fieldName);
		if (strYearA == null)
			strYearA = "";
		String strYearB = b.getDocument().get(fieldName);
		if (strYearB == null)
			strYearB = "";
		if (strYearA.length() == 0) // sort missing year at the end
			return strYearB.length() == 0 ? 0 : (reverse ? -1 : 1);
		if (strYearB.length() == 0) // sort missing year at the end
			return reverse ? 1 : -1;
		int year1;
		try {
			year1 = Integer.parseInt(strYearB);
			year1 -= year1 % 10;
		} catch (NumberFormatException e) {
			year1 = HitPropertyDocumentDecade.UNKNOWN_VALUE;
		}
		int year2;
		try {
			year2 = Integer.parseInt(strYearB);
			year2 -= year2 % 10;
		} catch (NumberFormatException e) {
			year2 = HitPropertyDocumentDecade.UNKNOWN_VALUE;
		}

		return reverse ? year2 - year1 : year1 - year2;
	}

	@Override
	public String getName() {
		return "decade";
	}

	public static DocPropertyDecade deserialize(String info) {
		return new DocPropertyDecade(info);
	}

	@Override
	public String serialize() {
		return serializeReverse() + PropValSerializeUtil.combineParts("decade", fieldName);
	}

}
