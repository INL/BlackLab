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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import nl.inl.util.UtilsForTesting;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestContentStoreDirFixedBlock {

	private ContentStore store;

	private File dir;

	String[] str = { "The quick brown fox ", "jumps over the lazy ", "dog.      ", "Leentje leerde Lotje lopen lan" };

	String[] doc = new String[4];

	@Before
	public void setUp() {

		// Remove any previously left over temp test dirs
		UtilsForTesting.removeBlackLabTestDirs();

		// Create new test dir
		dir = UtilsForTesting.createBlackLabTestDir("ContentStoreDirNew");

		store = new ContentStoreDirFixedBlock(dir, false);
		try {

			// Create four different documents that span different numbers of 4K blocks.
			Random random = new Random(12345);
			for (int i = 0; i < doc.length; i++) {
				StringBuilder b = new StringBuilder();
				for (int j = 0; j < i * 2400 + 800; j++) {
					char c = (char)('a' + random.nextInt(26));
					b.append(c);
				}
				doc[i] = b.toString();
			}

			// Store strings
			for (int i = 0; i < doc.length; i++) {
				Assert.assertEquals(i + 1, store.store(doc[i]));
			}
		} finally {
			store.close(); // close so everything is guaranteed to be written
		}
		store = new ContentStoreDirFixedBlock(dir, false);
	}

	@After
	public void tearDown() {
		store.close();
		// Try to remove (some files may be locked though)
		UtilsForTesting.removeBlackLabTestDirs();
	}

	@Test
	public void testRetrieve() {
		// Retrieve strings
		for (int i = 0; i < doc.length; i++) {
			Assert.assertEquals(doc[i], store.retrieve(i + 1));
		}
	}

	@Test
	public void testRetrievePart() {
		String[] parts = store.retrieveParts(2, new int[] { 5, 15 }, new int[] { 7, 18 });
		Assert.assertEquals(doc[1].substring(5, 7), parts[0]);
		Assert.assertEquals(doc[1].substring(15, 18), parts[1]);
	}

	@Test
	public void testDelete() {
		store.delete(2);
		Assert.assertNull(store.retrieve(2));
		Assert.assertEquals(doc[0], store.retrieve(1));
	}

	@Test
	public void testDeleteReuse() {
		store.delete(2);
		store.store(doc[3]);
		Assert.assertEquals(doc[3], store.retrieve(5));
	}

	@Test
	public void testDeleteReuseMultiple() {
		// Keep track of which documents are stored under which ids
		List<Integer> storedKeys = new ArrayList<>();
		Map<Integer, String> stored = new HashMap<>();
		for (int i = 1; i <= doc.length; i++) {
			storedKeys.add(i);
			stored.put(i, doc[i - 1]);
		}

		// Perform some random delete/add operations
		Random random = new Random(23456);
		final int OPERATIONS = 500;
		boolean testedClear = false;
		for (int i = 0; i < 500; i++) {

			if (i >= OPERATIONS / 2 && !testedClear) {
				// Halfway through the test, clear the whole content store.
				testedClear = true;
				store.clear();
				stored.clear();
				storedKeys.clear();
			}

			if (stored.size() > 0 && random.nextInt(3) == 0) {
				// Choose random document. Assert it was stored correctly, then delete it.
				int keyIndex = random.nextInt(stored.size());
				Integer key = storedKeys.remove(keyIndex);
				String docContents = stored.remove(key);
				assertDocumentStored(random, docContents, key);
				store.delete(key);
				Assert.assertTrue(store.isDeleted(key));
			} else {
				// Choose random document. Insert it and assert it was stored correctly.
				int docIndex = random.nextInt(doc.length);
				String docContents = doc[docIndex];
				int key = store.store(docContents);
				storedKeys.add(key);
				stored.put(key, docContents);
				assertDocumentStored(random, docContents, key);
			}
		}

		// Check that the status of ids in the store matches those in our storedKeys list
		Set<Integer> keysFromStore = store.getDocIds();
		int liveDocs = 0;
		for (Integer key: keysFromStore) {
			boolean isDeleted = store.isDeleted(key);
			if (!isDeleted)
				liveDocs++;
			Assert.assertEquals(isDeleted, !storedKeys.contains(key));
		}
		Assert.assertEquals(liveDocs, storedKeys.size());

		// Finally, check that all documents are still stored correctly
		for (int key: storedKeys) {
			String value = stored.get(key);
			assertDocumentStored(random, value, key);
		}
	}

	private void assertDocumentStored(Random random, String docContents, int key) {
		Assert.assertFalse(store.isDeleted(key));
		Assert.assertEquals(docContents.length(), store.getDocLength(key));
		if (random.nextBoolean()) {
			// Retrieve full document
			Assert.assertEquals(docContents, store.retrieve(key));
		} else {
			// Retrieve part of document
			int start = random.nextInt(docContents.length() - 10);
			int end = random.nextInt(docContents.length() - start) + start;
			String docPart = docContents.substring(start, end);
			String retrievedPart = store.retrievePart(key, start, end);
			Assert.assertEquals(docPart, retrievedPart);
		}
	}

	@Test
	public void testCloseReopen() {
		store.close();
		store = new ContentStoreDirFixedBlock(dir, false);
		Assert.assertEquals(doc[0], store.retrieve(1));
	}

	@Test
	public void testCloseReopenAppend() {
		store.close();
		store = new ContentStoreDirFixedBlock(dir, false);
		Assert.assertEquals(5, store.store("test"));
	}
}
