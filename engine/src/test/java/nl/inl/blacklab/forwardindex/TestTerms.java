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

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex.CollatorVersion;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.UtilsForTesting;

public class TestTerms {
    private static Terms t;

    final static String[] str = { "the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog" };

    private static File testDir;

    @BeforeClass
    public static void setUp() {

        // Create new test dir
        testDir = UtilsForTesting.createBlackLabTestDir("Terms");

        // Store some terms
        Collator coll = Collator.getInstance(new Locale("en", "GB"));
        Collators colls = new Collators(coll, CollatorVersion.V2);
        t = Terms.openForWriting(colls, null);
        if (t instanceof TermsWriter)
            ((TermsWriter) t).setMaxBlockSize(18);
        for (String s : str) {
            t.indexOf(s);
        }
        File f = new File(testDir, "terms.dat");
        t.write(f); // close so everything is guaranteed to be written

        // Open for reading
        t = Terms.openForReading(colls, f);
    }

    @AfterClass
    public static void tearDown() {
        // Try to remove (some files may be locked though)
        UtilsForTesting.removeBlackLabTestDir(testDir);
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
