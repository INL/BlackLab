package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.testutil.TestIndex;

public class TestDeleteDocument {

    static TestIndex testIndex;

    /**
     * Expected search results;
     */
    List<String> expected;

    @BeforeClass
    public static void setUp() {
        testIndex = new TestIndex(true);
    }

    @AfterClass
    public static void tearDown() {
        if (testIndex != null)
            testIndex.close();
    }

    @Test
    public void testSimple() {
        expected = Arrays.asList(
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" 'the' "));

        expected = Arrays.asList(
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" '(?-i)the' "));

        expected = Arrays.asList(
                "the [Force] be",
                "the [question]");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='nou'] "));
    }
}
