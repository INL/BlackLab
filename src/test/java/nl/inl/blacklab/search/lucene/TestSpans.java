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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSpans {
	private Spans results;

	@Before
	public void setUp() {
		results = HelpersForTests.getSimpleResults(3);
	}

	@Test
	public void testNext() throws IOException {
		results.next();
		Assert.assertEquals(1, results.doc());
		results.next();
		Assert.assertEquals(4, results.doc());
		results.next();
		Assert.assertEquals(7, results.doc());
		Assert.assertFalse(results.next());
	}

}
