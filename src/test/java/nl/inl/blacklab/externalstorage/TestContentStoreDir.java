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
package nl.inl.blacklab.externalstorage;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.util.Utilities;

public class TestContentStoreDir {
	private ContentStore store;

	private File dir;

	String[] str = { "The quick brown fox ", "jumps over the lazy ", "dog.                " };

	@Before
	public void setUp() {
		// Remove any previously left over temp test dirs
		Utilities.removeBlackLabTestDirs();

		// Create new test dir
		dir = Utilities.createBlackLabTestDir("ContentStoreDir");

		store = new ContentStoreDir(dir);
		((ContentStoreDir) store).setDataFileSizeHint(60); // 60 bytes per data file (1.5 strings)

		// Store strings
		for (int i = 0; i < str.length; i++) {
			Assert.assertEquals(i + 1, store.store(str[i]));
		}
	}

	@After
	public void tearDown() {
		store.close();
		// Try to remove (some files may be locked though)
		Utilities.removeBlackLabTestDirs();
	}

	@Test
	public void testRetrieve() {
		// Retrieve strings
		for (int i = 0; i < str.length; i++) {
			Assert.assertEquals(str[i], store.retrieve(i + 1));
		}
	}

	@Test
	public void testRetrievePart() {
		String[] parts = store.retrieveParts(2, new int[] { 5, 15 }, new int[] { 7, 18 });
		Assert.assertEquals(str[1].substring(5, 7), parts[0]);
		Assert.assertEquals(str[1].substring(15, 18), parts[1]);
	}

	@Test
	public void testDelete() {
		store.delete(2);
		Assert.assertNull(store.retrieve(2));
		Assert.assertEquals(str[0], store.retrieve(1));
	}

	@Test
	public void testCloseReopen() {
		store.close();
		store = new ContentStoreDir(dir);
		Assert.assertEquals(str[0], store.retrieve(1));
	}

	@Test
	public void testCloseReopenAppend() {
		store.close();
		store = new ContentStoreDir(dir);
		Assert.assertEquals(4, store.store("test"));
	}
}
