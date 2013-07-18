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
import java.util.Arrays;

import junit.framework.Assert;
import nl.inl.util.Utilities;

import org.junit.After;
import org.junit.Test;

public class TestForwardIndexV2 {
	private ForwardIndex fi;

	private File dir;

	String[][] str = { { "How", "much", "wood" }, { "would", "a", "woodchuck", "chuck" },
			{ "if", "a", "woodchuck", "could", "chuck", "wood" } };

	private void setUpForwardIndex() {
		// Remove any previously left over temp test dirs
		Utilities.removeBlackLabTestDirs();

		// Create new test dir
		dir = Utilities.createBlackLabTestDir("ForwardIndex");

		fi = new ForwardIndexImplV2(dir, true, null, true);
		try {
			// Store strings
			for (int i = 0; i < str.length; i++) {
				Assert.assertEquals(i, fi.addDocument(Arrays.asList(str[i])));
			}
		} finally {
			fi.close(); // close so everything is guaranteed to be written
		}
		fi = new ForwardIndexImplV2(dir, false, null, false);
	}

	@After
	public void tearDown() {
		if (fi != null)
			fi.close();
		// Try to remove (some files may be locked though)
		Utilities.removeBlackLabTestDirs();
	}

	public String[] retrievePart(int id, int start, int end) {
		return fi.retrieveParts(id, new int[] { start }, new int[] { end }).get(0);
	}

	@Test
	public void testRetrieve() {
		ForwardIndexImplV2.preferredChunkSizeBytes = Integer.MAX_VALUE; // make sure this is at the default
		ForwardIndexImplV2.keepInMemoryIfPossible = true; // default
		ForwardIndexImplV2.useMemoryMapping = true; // default
		setUpForwardIndex();

		// Retrieve strings
		for (int i = 0; i < str.length; i++) {
			Assert.assertEquals(Arrays.asList(str[i]), Arrays.asList(retrievePart(i, -1, -1)));
		}
	}

	@Test
	public void testChunkingInMemory() {
		ForwardIndexImplV2.preferredChunkSizeBytes = 24; // really small so chunked mapping is forced
		ForwardIndexImplV2.keepInMemoryIfPossible = true; // default
		ForwardIndexImplV2.useMemoryMapping = true; // default
		setUpForwardIndex();

		// Retrieve strings
		for (int i = 0; i < str.length; i++) {
			Assert.assertEquals(Arrays.asList(str[i]), Arrays.asList(retrievePart(i, -1, -1)));
		}
	}

	@Test
	public void testChunkingMapped() {
		ForwardIndexImplV2.preferredChunkSizeBytes = 24; // really small so chunked mapping is forced
		ForwardIndexImplV2.keepInMemoryIfPossible = false; // test mapped access
		ForwardIndexImplV2.useMemoryMapping = true; // default
		setUpForwardIndex();

		// Retrieve strings
		for (int i = 0; i < str.length; i++) {
			Assert.assertEquals(Arrays.asList(str[i]), Arrays.asList(retrievePart(i, -1, -1)));
		}
	}

	@Test
	public void testChunkingFileChannel() {
		ForwardIndexImplV2.preferredChunkSizeBytes = Integer.MAX_VALUE; // default
		ForwardIndexImplV2.keepInMemoryIfPossible = false; // test direct file channel access
		ForwardIndexImplV2.useMemoryMapping = false; // test direct file channel access
		setUpForwardIndex();

		// Retrieve strings
		for (int i = 0; i < str.length; i++) {
			Assert.assertEquals(Arrays.asList(str[i]), Arrays.asList(retrievePart(i, -1, -1)));
		}
	}

}
