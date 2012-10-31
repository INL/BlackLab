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
import java.io.FilenameFilter;
import java.util.UUID;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestContentStoreDirUtf8 {
	private ContentStore store;

	private File dir;

	String[] str = { "The quick brown fox ", "jumps over the lazy ", "dog.           ", "Leentje leerde Lotje lopen lan" };

	public boolean deleteContentStoreDirectory() {
		if (dir.exists()) {
			File[] files = dir.listFiles();
			for (int i = 0; i < files.length; i++) {
				files[i].delete(); // may fail because of mem-map write lock; will be deleted next time
			}
		}
		return (dir.delete());
	}

	@Before
	public void setUp() {
		File tempDir = new File(System.getProperty("java.io.tmpdir"));
		if (!tempDir.exists())
			throw new RuntimeException("Directory " + tempDir
					+ " must exist to run this test.");

		// Remove old ContentStore test dirs from temp dir, if possible
		// (may not be possible because of memory mapping lock on Windows;
		//  in this case we just leave the files and continue)
		for (File d: tempDir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File parentDir, String name) {
					return name.startsWith("BlackLabTest");
				}
			})) {

			for (File f: d.listFiles()) {
				f.delete();
			}
			d.delete();
		}

		dir = new File(tempDir, "BlackLabTest_ContentStore_" + UUID.randomUUID().toString());

		store = new ContentStoreDirUtf8(dir);
		try {
			((ContentStoreDirUtf8) store).setBlockSizeCharacters(15); // block size in characters
			((ContentStoreDirUtf8) store).setDataFileSizeHint(25); // data file size in bytes
			((ContentStoreDirUtf8) store).setWriteMapReserve(40); // how much space to reserve at
																	// end of file for writing

			// Store strings
			for (int i = 0; i < str.length; i++) {
				Assert.assertEquals(i + 1, store.store(str[i]));
			}
		} finally {
			store.close(); // close so everything is guaranteed to be written
		}
		store = new ContentStoreDirUtf8(dir);
	}

	@After
	public void tearDown() {
		if (store != null)
			store.close();
		deleteContentStoreDirectory();
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
		store = new ContentStoreDirUtf8(dir);
		Assert.assertEquals(str[0], store.retrieve(1));
	}

	@Test
	public void testCloseReopenAppend() {
		store.close();
		store = new ContentStoreDirUtf8(dir);
		Assert.assertEquals(5, store.store("test"));
	}
}
