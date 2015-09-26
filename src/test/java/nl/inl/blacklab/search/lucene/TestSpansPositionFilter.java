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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;
import org.junit.Test;

import nl.inl.blacklab.MockSpans;
import nl.inl.blacklab.TestUtil;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter.Filter;

public class TestSpansPositionFilter {
	
	private SpansPositionFilter getSpans(boolean swap, Filter type) {
		Spans a = MockSpans.fromLists(
			new int[] {  1,  1,  2,  2,  2,  3 },
			new int[] { 10, 20, 10, 10, 30, 20 },
			new int[] { 15, 25, 15, 20, 35, 25 }
		);
		Spans b = MockSpans.fromLists(
			new int[] {  1,  2,  3 }, 
			new int[] { 11, 22, 20 }, 
			new int[] { 12, 23, 25 }
		);
		return new SpansPositionFilter(swap ? b : a, swap ? a : b, type);
	}

	@Test
	public void testContaining() throws IOException {
		SpansPositionFilter spans = getSpans(false, Filter.CONTAINING);
		Spans exp = MockSpans.fromLists(
			new int[] { 1,  3},
			new int[] {10, 20}, 
			new int[] {15, 25}
		);
		TestUtil.assertEquals(exp, spans);
	}

	@Test
	public void testWithin() throws IOException {
		SpansPositionFilter spans = getSpans(true, Filter.WITHIN);
		Spans exp = MockSpans.fromLists(
			new int[] {  1,  3 },
			new int[] { 11, 20 },
			new int[] { 12, 25 }
		);
		TestUtil.assertEquals(exp, spans);
	}

}
