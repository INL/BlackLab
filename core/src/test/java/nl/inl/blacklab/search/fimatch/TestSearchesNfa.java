package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.testutil.TestIndex;

public class TestSearchesNfa {

    static TestIndex testIndex;

    /**
     * Expected search results;
     */
    List<String> expected;

    @BeforeClass
    public static void setUp() {
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.MAX_NFA_MATCHING);
        testIndex = new TestIndex();
    }

    @AfterClass
    public static void tearDown() {
        if (testIndex != null)
            testIndex.close();
        ClauseCombinerNfa.setNfaThreshold(ClauseCombinerNfa.DEFAULT_NFA_THRESHOLD);
    }

    @Test
    public void testSequence1() {
        expected = Arrays.asList("[May the] Force");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' "));
    }

    @Test
    public void testSequence2a() {
        expected = Arrays.asList("[May the Force be with you]");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' ('force' 'be' 'with') 'you' "));
    }

    @Test
    public void testSequence2b() {
        expected = Arrays.asList("[May the Force be with you]");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' 'force' 'be' 'with' 'you' "));
    }

    @Test
    public void testSequence3() {
        expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'Force' "));
    }

    @Test
    public void testRepetition0() {
        expected = Arrays.asList("[May the] Force");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the'+ "));
    }

    @Test
    public void testRepetition1() {
        expected = Arrays.asList("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '.*e'+ 'with' "));
    }

    @Test
    public void testRepetition2() {
        expected = Arrays.asList("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '(?-i).*e'{2,3} 'with' "));
    }

    @Test
    public void testRepetition3() {
        expected = Arrays.asList("[May the] Force");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'dsgsdg'* 'the' "));
    }

    @Test
    public void testRepetition4() {
        expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'dsgsdg'+ 'the' "));
    }

    @Test
    public void testRepetitionCaseSensitive() {
        expected = Arrays.asList("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' '(?-i).*e'+ 'with' "));
    }

    @Test
    public void testCaseInsensitive() {
        expected = Arrays.asList("[The quick] brown", "May [the Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'the' '.*c.' "));
    }

    @Test
    public void testExpansion1() {
        expected = Arrays.asList("[May the Force be with] you");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []{2,3} 'with' "));
    }

    @Test
    public void testExpansion2() {
        expected = Arrays.asList("[May the Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []{0,2} 'Force' "));
    }

    @Test
    public void testExpansion3() {
        expected = Collections.emptyList();
        Assert.assertEquals(expected, testIndex.findConc(" 'May' 'the' []+ 'Force' "));
    }

    @Test
    public void testExpansion4() {
        expected = Arrays.asList("[May the Force] be");
        Assert.assertEquals(expected, testIndex.findConc(" 'May' []+ 'Force' "));
    }

//	@Test
//	public void testRelativeFreqs1() {
//		expected = Arrays.asList("aap [noot mier aap] noot");
//		Assert.assertEquals(expected, testIndex.findConc(" 'noot' 'mier' 'aap' "));
//	}

    @Test
    public void testRelativeFreqs2() {
        expected = Arrays.asList("[noot mier aap] mier");
        Assert.assertEquals(expected, testIndex.findConc(" 'noot' 'mier' 'aap' "));
    }

    @Test
    public void testOverlappingSequences() {
        expected = Arrays.asList("aap [mier mier mier noot noot] aap", "mier [mier mier noot noot] aap");
        Assert.assertEquals(expected, testIndex.findConc(" 'mier' []{1,2} 'noot' 'noot' "));
    }

    @Test
    public void testSuffix() {
        expected = Arrays.asList("[The quick] brown");
        Assert.assertEquals(expected, testIndex.findConc("\".*E\" \"quick\""));
    }

    @Test
    public void testNegation() {
        expected = Arrays.asList("mier [mier noot noot aap] aap");
        Assert.assertEquals(expected, testIndex.findConc("'mier' [word != 'aap|mier']+ 'aap'"));
    }

//
//	@Test
//	public void testRelativeFreqs3() {
//		expected = Arrays.asList("noot [mier aap noot] aap");
//		Assert.assertEquals(expected, testIndex.findConc(" 'mier' 'aap' 'noot' "));
//	}

}
