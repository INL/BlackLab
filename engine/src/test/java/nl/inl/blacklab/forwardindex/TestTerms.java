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

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex.CollatorVersion;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.UtilsForTesting;

public class TestTerms {
    private Terms t;

    private File dir;

    String[] str = { "the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog" };

    @Before
    public void setUp() {

        // Remove any previously left over temp test dirs
        UtilsForTesting.removeBlackLabTestDirs();

        // Create new test dir
        dir = UtilsForTesting.createBlackLabTestDir("Terms");

        // Store some terms
        Collator coll = Collator.getInstance(new Locale("en", "GB"));
        Collators colls = new Collators(coll, CollatorVersion.V2);
        t = Terms.openForWriting(colls, null, true);
        if (t instanceof TermsWriter)
            ((TermsWriter) t).setMaxBlockSize(18);
        for (int i = 0; i < str.length; i++) {
            t.indexOf(str[i]);
        }
        File f = new File(dir, "terms.dat");
        t.write(f); // close so everything is guaranteed to be written

        // Open for reading
        t = Terms.openForReading(colls, f, true, true);
    }

    @After
    public void tearDown() {
        // Try to remove (some files may be locked though)
        UtilsForTesting.removeBlackLabTestDirs();
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
            Assert.assertEquals(expected[t.idToSortPosition(i, MatchSensitivity.SENSITIVE)], t.get(i));
        }
    }

    /**
     * Test if the "reverse sort positions" are determined correctly.
     */
    @Test
    public void testReverseOrder() {
        int[] expected = { 7, 6, 0, 2, 3, 5, 4, 1 };
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], t.idToSortPosition(i, MatchSensitivity.SENSITIVE));
        }
    }

    /**
     * Test if the "reverse sort positions" are determined correctly.
     */
    @Test
    public void testIndexOf() {
        String[] input = {
                "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog"
        };
        int[] expected = { 0, 1, 2, 3, 4, 5, 6, 7 };
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], t.indexOf(input[i]));
        }
    }

    /**
     * Test if the "reverse sort positions" are determined correctly.
     */
    @Test
    public void testIndexOfInsensitive() {
        String[] input = {
                "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog"
        };
        int[] expected = { 0, 1, 2, 3, 4, 5, 6, 7 };
        for (int i = 0; i < expected.length; i++) {
            MutableIntSet results = new IntHashSet();
            t.indexOf(results, input[i], MatchSensitivity.INSENSITIVE);
            Assert.assertEquals(expected[i], results.intIterator().next());
        }
    }
}
