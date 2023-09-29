package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.text.Collator;
import java.util.Locale;

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.Collators.CollatorVersion;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.UtilsForTesting;

public class TestTerms {
    public static final String[] TEST_SENTENCE = { "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog" };

    private static TermsReader t;

    private static UtilsForTesting.TestDir testDir;

    @BeforeClass
    public static void setUp() {

        // Create new test dir
        testDir = UtilsForTesting.createBlackLabTestDir("Terms");

        // Store some terms
        Collator coll = Collator.getInstance(new Locale("en", "GB"));
        Collators colls = new Collators(coll, CollatorVersion.V2);
        TermsWriter tw = TermsExternalUtil.openForWriting(colls, null);
        tw.setMaxBlockSize(18);
        for (String s: TEST_SENTENCE) {
            tw.indexOf(s);
        }
        File f = new File(testDir.file(), "terms.dat");
        tw.write(f); // close so everything is guaranteed to be written

        // Open for reading
        t = TermsExternalUtil.openForReading(colls, f);
    }

    @AfterClass
    public static void tearDown() {
        // Try to remove (some files may be locked though)
        testDir.close();
    }

    /**
     * Test if the terms were stored correctly.
     */
    @Test
    public void testRetrieve() {
        for (int i = 0; i < TEST_SENTENCE.length; i++) {
            Assert.assertEquals(TEST_SENTENCE[i], t.get(i));
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

    @Test
    public void testIndexOf() {
        int[] expected = { 0, 1, 2, 3, 4, 5, 6, 7 };
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], t.indexOf(TEST_SENTENCE[i]));
        }
    }

    @Test
    public void testIndexOfInsensitive() {
        int[] expected = { 0, 1, 2, 3, 4, 5, 6, 7 };
        for (int i = 0; i < expected.length; i++) {
            MutableIntSet results = new IntHashSet();
            t.indexOf(results, TEST_SENTENCE[i], MatchSensitivity.INSENSITIVE);
            Assert.assertEquals(expected[i], results.intIterator().next());
        }
    }
}
