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

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;

/**
 * A hit property for grouping on by decade based on a stored field
 * in the corresponding Lucene document containing a year.
 */
public class HitPropertyDocumentDecade extends HitProperty {

	/** The value we store when the decade is unknown */
	public static final int UNKNOWN_VALUE = 10000000;

	IndexReader reader;

	String fieldName;

	public HitPropertyDocumentDecade(Hits hits, String fieldName) {
		super(hits);
		this.reader = hits.getSearcher().getIndexReader();
		this.fieldName = fieldName;
	}

	@Override
	public HitPropValueDecade get(int hitNumber) {
		try {
			Hit result = hits.getByOriginalOrder(hitNumber);
			Document d = reader.document(result.doc);
			String strYear = d.get(fieldName);
			int year;
			try {
				year = Integer.parseInt(strYear);
				year -= year % 10;
			} catch (NumberFormatException e) {
				year = UNKNOWN_VALUE;
			}
			return new HitPropValueDecade(year);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int compare(Object i, Object j) {
		try {
			Hit a = hits.getByOriginalOrder((Integer)i);
			Hit b = hits.getByOriginalOrder((Integer)j);
			Document d = reader.document(a.doc);
			String strYearA = d.get(fieldName);
			if (strYearA == null)
				strYearA = "";
			d = reader.document(b.doc);
			String strYearB = d.get(fieldName);
			if (strYearB == null)
				strYearB = "";
			if (strYearA.length() == 0) // sort missing year at the end
				return strYearB.length() == 0 ? 0 : (reverse ? -1 : 1);
			if (strYearB.length() == 0) // sort missing year at the end
				return reverse ? 1 : -1;
			int aYear;
			try {
				aYear = Integer.parseInt(strYearA);
				aYear -= aYear % 10;
			} catch (NumberFormatException e) {
				aYear = UNKNOWN_VALUE;
			}
			int bYear;
			try {
				bYear = Integer.parseInt(strYearB);
				bYear -= bYear % 10;
			} catch (NumberFormatException e) {
				bYear = UNKNOWN_VALUE;
			}

			return reverse ? bYear - aYear : aYear - bYear;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getName() {
		return "decade";
	}

	@Override
	public String serialize() {
		return serializeReverse() + PropValSerializeUtil.combineParts("decade", fieldName);
	}

	public static HitPropertyDocumentDecade deserialize(Hits hits, String info) {
		return new HitPropertyDocumentDecade(hits, info);
	}
}
