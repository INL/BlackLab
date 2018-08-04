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
package nl.inl.blacklab.contentstore;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.util.UtilsForTesting;

public class TestContentStoreDirUtf8 {
    private ContentStore store;

    private File dir;

    String[] str = { "The quick brown fox ", "jumps over the lazy ", "dog.           ",
            "Leentje leerde Lotje lopen lan" };

    @Before
    public void setUp() {
        // Remove any previously left over temp test dirs
        UtilsForTesting.removeBlackLabTestDirs();

        // Create new test dir
        dir = UtilsForTesting.createBlackLabTestDir("ContentStoreDirUtf8");

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
        // Try to remove (some files may be locked though)
        UtilsForTesting.removeBlackLabTestDirs();
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
