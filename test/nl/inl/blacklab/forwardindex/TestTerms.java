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
package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.text.Collator;
import java.util.Locale;

import junit.framework.Assert;
import nl.inl.util.Utilities;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestTerms {
	private Terms t;

	private File dir;

	String[] str = { "the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog" };

	@Before
	public void setUp() {

		// Remove any previously left over temp test dirs
		Utilities.removeBlackLabTestDirs();

		// Create new test dir
		dir = Utilities.createBlackLabTestDir("Terms");

		// Store some terms
		t = new Terms(true, Collator.getInstance(new Locale("en", "GB")));
		for (int i = 0; i < str.length; i++) {
			t.indexOf(str[i]);
		}
		File f = new File(dir, "terms.dat");
		t.write(f); // close so everything is guaranteed to be written

		// Open for reading
		t = new Terms(false, Collator.getInstance(new Locale("en", "GB")), f);
	}

	@After
	public void tearDown() {
		// Try to remove (some files may be locked though)
		Utilities.removeBlackLabTestDirs();
	}

	/**
	 * Test if the terms were stored correctly.
	 */
	@Test
	public void testRetrieve() {
		String[] expected = { "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog" };
		for (int i = 0; i < expected.length; i++) {
			Assert.assertEquals(expected[i], t.get(i));
		}
	}

	/**
	 * Test if the sort positions are stored correctly.
	 */
	@Test
	public void testOrder() {
		String[] expected = {
				"brown",
				"dog",
				"fox",
				"jumps",
				"lazy",
				"over",
				"quick",
				"the"
				};
		for (int i = 0; i < expected.length; i++) {
			Assert.assertEquals(expected[i], t.get(t.sortPositionToId(i)));
		}
	}

	/**
	 * Test if the "reverse sort positions" are determined correctly.
	 */
	@Test
	public void testReverseOrder() {
		int[] expected = { 7, 6, 0, 2, 3, 5, 4, 1 };
		for (int i = 0; i < expected.length; i++) {
			Assert.assertEquals(expected[i], t.idToSortPosition(i));
		}
	}
}
