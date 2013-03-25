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

import junit.framework.Assert;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.lucene.SpansStub;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

public class TestDocResults {

	@Test
	public void testDocResultsIterate() {
		int[] aDoc = new int[] { 1, 1, 2, 3, 3 };
		int[] aStart = new int[] { 1, 2, 3, 4, 5 };
		int[] aEnd = new int[] { 2, 3, 4, 5, 6 };
		Spans a = new SpansStub(aDoc, aStart, aEnd);

		@SuppressWarnings("deprecation")
		Hits hits = new Hits(null, null, a);
		DocResults drs = new DocResults(null, hits);

		int[] expDoc = new int[] {1, 2, 3};
		int[] expHits = new int[] {2, 1, 2};
		int i = 0;
		for (DocResult dr: drs) {
			Assert.assertEquals(expDoc[i], dr.getDocId());
			Assert.assertEquals(expHits[i], dr.getHits().size());
			i++;
		}
	}

}
