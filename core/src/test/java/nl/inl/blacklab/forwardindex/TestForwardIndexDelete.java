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
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.util.UtilsForTesting;

public class TestForwardIndexDelete {
    private AnnotationForwardIndex fi;

    private File dir;

    @Before
    public void setUpForwardIndex() {

        // Lengths of documents to add to forward index
        Integer[] docLengths = { 10, 12, 14, 16, 18, 20 };

        // Whether or not to delete the documents again
        boolean[] delDoc = { true, false, true, false, true, false };

        // Remove any previously left over temp test dirs
        UtilsForTesting.removeBlackLabTestDirs();

        // Create new test dir
        dir = UtilsForTesting.createBlackLabTestDir("ForwardIndexDelete");

        fi = new AnnotationForwardIndexWriter(null, dir, Collators.defaultCollator(), true, true);
        // Store strings
        List<Integer> toDelete = new ArrayList<>();
        for (int j = 0; j < docLengths.length; j++) {
            int length = docLengths[j];
            int fiid = addDocumentOfLength(length);

            // See if we want to delete the doc again
            if (delDoc[j])
                toDelete.add(fiid);
        }
        // Delete docs
        for (Integer fiid : toDelete) {
            fi.deleteDocument(fiid);
        }
    }

    private int addDocumentOfLength(int length) {
        List<String> content = new ArrayList<>();
        // Make test doc: first token is 0, each subsequent
        // token is one more. Corresponds to term ids.
        for (int i = 0; i < length; i++) {
            content.add(Integer.toString(i));
        }
        int fiid = fi.addDocument(content);

        // Test retrieve
        int[] start = { 0 };
        int[] end = { length };
        int[] test = fi.retrievePartsInt(fiid, start, end).get(0);
        for (int i = 0; i < length; i++) {
            Assert.assertEquals(i, test[i]);
        }

        return fiid;
    }

    @After
    public void tearDown() {
        if (fi != null)
            fi.close();
        // Try to remove (some files may be locked though)
        UtilsForTesting.removeBlackLabTestDirs();
    }

    /** Adding a document the exact length of a gap. */
    @Test
    public void testExactFit() {
        Assert.assertEquals(2, addDocumentOfLength(14));
    }

    /** Adding a document smaller than a gap. */
    @Test
    public void testInexactFit() {
        Assert.assertEquals(6, addDocumentOfLength(15)); // use gap 18, new entry, leave gap 3 at index 4
        Assert.assertEquals(4, addDocumentOfLength(3)); // fill in gap 3 at index 4
        Assert.assertEquals(7, addDocumentOfLength(1)); // use gap 10, new entry
    }

    /** Adding a document larger than any gap. */
    @Test
    public void testNoFit() {
        Assert.assertEquals(6, addDocumentOfLength(19)); // no room, new entry
        Assert.assertEquals(4, addDocumentOfLength(18)); // exact fit
        Assert.assertEquals(2, addDocumentOfLength(14)); // exact fit
        Assert.assertEquals(0, addDocumentOfLength(10)); // exact fit
        Assert.assertEquals(7, addDocumentOfLength(1)); // no fit, new entry
    }

    /** Deleting a document causing a merge. */
    @Test
    public void testMerge() {
        fi.deleteDocument(3); // delete doc len 16, merge with len 14 and 18 (total 48)
        // Check that adding stuff doesn't create new indices but reuses old ones
        Assert.assertTrue(addDocumentOfLength(46) < 5); // re-uses freed entry
        Assert.assertTrue(addDocumentOfLength(1) < 5); // re-uses freed entry
        Assert.assertTrue(addDocumentOfLength(1) < 5); // exact fit
        Assert.assertTrue(addDocumentOfLength(10) < 5); // exact fit
    }

    /** Deleting a document at the end, causing a merge and a truncate. */
    @Test
    public void testDeleteAtEnd() {
        fi.deleteDocument(5); // delete doc len 20 at end, merge with len 18 (total 38)
        Assert.assertTrue(addDocumentOfLength(37) <= 5); // re-uses freed entry
        Assert.assertTrue(addDocumentOfLength(1) <= 5); // uses gap len 10, re-uses freed entry
        Assert.assertEquals(0, addDocumentOfLength(9)); // exact fit
    }

}
